package com.lusanetech.warmhearth.health

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.util.Links
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.reflect.KClass

/**
 * Watch / band readings through Health Connect (Google Fit's APIs shut down at the end of 2026).
 * The watch's own app (Samsung Health, Fitbit, Mi Fitness, Zepp…) writes to Health Connect; we only
 * read, only what the senior agreed to, and only to show it to their linked family.
 *
 * Play policy: health data is never used for ads or product suggestions (the device list in the
 * family app is the same for everyone) and never sold or shared beyond linked family.
 */
object HealthVitals {
    const val PROVIDER = "com.google.android.apps.healthdata"
    private const val MIN_GAP_MS = 30 * 60_000L

    private val TYPES: List<KClass<out Record>> = listOf(
        StepsRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, SleepSessionRecord::class,
        OxygenSaturationRecord::class, BloodPressureRecord::class, BloodGlucoseRecord::class,
    )

    val PERMISSIONS: Set<String> =
        TYPES.map { HealthPermission.getReadPermission(it) }.toSet() + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    enum class Availability { READY, NEEDS_INSTALL, UNSUPPORTED }

    fun availability(c: Context): Availability = when (HealthConnectClient.getSdkStatus(c, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.READY
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL
        else -> Availability.UNSUPPORTED
    }

    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .setPackage("com.android.vending")

    /** For rememberLauncherForActivityResult: shows Health Connect's own permission screen. */
    fun requestContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun grantedCount(c: Context): Int = runCatching {
        HealthConnectClient.getOrCreate(c).permissionController.getGrantedPermissions().count { it in PERMISSIONS }
    }.getOrDefault(0)

    /** Reads today's summary and the latest readings, then writes them for the family. Throttled unless [force]. */
    suspend fun sync(context: Context, force: Boolean = false) {
        val c = context.applicationContext
        val graph = c.graph
        val s = graph.prefs.current()
        if (s.mode != AppMode.SENIOR || !s.vitalsShared || s.deviceId.isBlank()) return
        if (availability(c) != Availability.READY) return
        val sp = c.getSharedPreferences("vitals", Context.MODE_PRIVATE)
        if (!force && System.currentTimeMillis() - sp.getLong("last", 0L) < MIN_GAP_MS) return
        if (graph.family.uid() == null) return

        val client = HealthConnectClient.getOrCreate(c)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        if (granted.none { it in PERMISSIONS }) return
        fun can(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted
        // In the background (WorkManager) Health Connect also needs the background-read permission.
        val bgOk = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted &&
            runCatching { client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE }.getOrDefault(false)
        if (!bgOk && !isForeground()) return

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val sources = LinkedHashSet<String>()
        val out = hashMapOf<String, Any?>()

        if (can(StepsRecord::class)) runCatching {
            val agg = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(dayStart, now)))
            out["stepsToday"] = agg[StepsRecord.COUNT_TOTAL]
        }
        if (can(HeartRateRecord::class)) runCatching {
            val agg = client.aggregate(
                AggregateRequest(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX), TimeRangeFilter.between(dayStart, now)),
            )
            out["hrAvg"] = agg[HeartRateRecord.BPM_AVG]; out["hrMin"] = agg[HeartRateRecord.BPM_MIN]; out["hrMax"] = agg[HeartRateRecord.BPM_MAX]
            latest(client, HeartRateRecord::class, now, Duration.ofHours(12))?.let { r ->
                r.samples.maxByOrNull { it.time }?.let { out["heartRate"] = it.beatsPerMinute; out["heartRateAt"] = it.time.toEpochMilli() }
                sources += r.metadata.dataOrigin.packageName
            }
        }
        if (can(RestingHeartRateRecord::class)) runCatching {
            latest(client, RestingHeartRateRecord::class, now, Duration.ofDays(3))?.let { out["restingHeartRate"] = it.beatsPerMinute; sources += it.metadata.dataOrigin.packageName }
        }
        if (can(SleepSessionRecord::class)) runCatching {
            // Last night: sessions that ended in the last 20 hours.
            val sessions = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(30)), now))).records
                .filter { it.endTime.isAfter(now.minus(Duration.ofHours(20))) }
            if (sessions.isNotEmpty()) {
                out["sleepMinutes"] = sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                sources += sessions.first().metadata.dataOrigin.packageName
            }
        }
        if (can(OxygenSaturationRecord::class)) runCatching {
            latest(client, OxygenSaturationRecord::class, now, Duration.ofDays(2))?.let { out["spo2"] = it.percentage.value; sources += it.metadata.dataOrigin.packageName }
        }
        if (can(BloodPressureRecord::class)) runCatching {
            latest(client, BloodPressureRecord::class, now, Duration.ofDays(7))?.let {
                out["bpSystolic"] = it.systolic.inMillimetersOfMercury.toInt(); out["bpDiastolic"] = it.diastolic.inMillimetersOfMercury.toInt()
                out["bpAt"] = it.time.toEpochMilli(); sources += it.metadata.dataOrigin.packageName
            }
        }
        if (can(BloodGlucoseRecord::class)) runCatching {
            latest(client, BloodGlucoseRecord::class, now, Duration.ofDays(7))?.let {
                out["glucoseMgDl"] = it.level.inMilligramsPerDeciliter.toInt(); out["glucoseAt"] = it.time.toEpochMilli()
                sources += it.metadata.dataOrigin.packageName
            }
        }

        out["sources"] = sources.mapNotNull { appLabel(c, it) }.distinct().take(3)
        out["syncedAt"] = FieldValue.serverTimestamp()
        val fs = FirebaseFirestore.getInstance()
        val doc = fs.collection("devices").document(s.deviceId)
        doc.set(mapOf("vitals" to out), SetOptions.merge())
        // One small doc per day for the family's 7-day view; deleted after 90 days (TTL).
        val day = LocalDate.now(zone).toString()
        val daily = out.filterKeys { it in setOf("stepsToday", "hrAvg", "hrMin", "hrMax", "restingHeartRate", "sleepMinutes", "spo2", "bpSystolic", "bpDiastolic", "glucoseMgDl") } +
            mapOf("date" to day, "expireAt" to Timestamp(Date(System.currentTimeMillis() + 90L * 86_400_000)))
        val write = doc.collection("vitals").document(day).set(daily, SetOptions.merge())
        sp.edit().putLong("last", System.currentTimeMillis()).apply()
        withTimeoutOrNull(10_000) { runCatching { write.await() } }
    }

    /** Stop sharing: remove what the family can see. */
    suspend fun stopSharing(context: Context) {
        val graph = context.graph
        graph.prefs.setVitalsShared(false)
        val id = graph.prefs.current().deviceId.takeIf { it.isNotBlank() } ?: return
        runCatching { FirebaseFirestore.getInstance().collection("devices").document(id).update("vitals", FieldValue.delete()).await() }
    }

    private suspend fun <T : Record> latest(client: HealthConnectClient, type: KClass<T>, now: Instant, window: Duration): T? =
        client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(now.minus(window), now), ascendingOrder = false, pageSize = 1)).records.firstOrNull()

    private fun appLabel(c: Context, pkg: String): String? = runCatching {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    @Volatile var foreground = false
    private fun isForeground() = foreground
}

/** Health Connect's "why does this app need access?" link: opens our privacy policy, health section. */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Links.open(this, "/privacy#health")
        finish()
    }
}
