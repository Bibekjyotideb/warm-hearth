package com.lusanetech.warmhearth.family

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.lusanetech.warmhearth.data.Medicine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * The family phone's remote control for a parent's phone: live status, settings, commands
 * (ring / ringer on), photo notes and today's medicines. Linking lives in FamilyRepository.
 */
class RemoteControl(private val context: Context) {
    private val fs by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private fun device(id: String) = fs.collection("devices").document(id)
    private val me get() = auth.currentUser
    private val myFirstName get() = me?.displayName?.trim()?.substringBefore(' ').orEmpty()

    companion object { private const val TAG = "Remote" }

    fun observeDevices(): Flow<List<DeviceState>> = callbackFlow {
        val uid = me?.uid
        if (uid == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val reg = fs.collection("devices").whereArrayContains("family", uid).addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map(DeviceState::from)?.sortedBy { it.name.lowercase() } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    /** Premium only (enforced by Firestore rules too). */
    suspend fun saveSettings(deviceId: String, settings: DeviceSettings): Boolean = runCatching {
        device(deviceId).update("settings", settings.copy(updatedAt = System.currentTimeMillis()).toMap()).await()
        true
    }.getOrElse { Log.w(TAG, "settings", it); false }

    /** "ring", "ringer_on" or "sync". Returns the command id to watch, or null if it couldn't be sent. */
    suspend fun sendCommand(deviceId: String, type: String): String? = runCatching {
        val uid = me?.uid ?: return null
        device(deviceId).collection("commands").add(
            mapOf("type" to type, "byUid" to uid, "byName" to myFirstName, "createdAt" to FieldValue.serverTimestamp()),
        ).await().id
    }.getOrElse { Log.w(TAG, "command", it); null }

    /** Emits the command's result: null while pending, then "ok", "ringing", "no_access", or "error:…". */
    fun observeCommand(deviceId: String, commandId: String): Flow<String?> = callbackFlow {
        val reg = device(deviceId).collection("commands").document(commandId).addSnapshotListener { snap, _ ->
            val err = snap?.getString("error")
            trySend(if (err != null) "error:$err" else snap?.getString("result"))
        }
        awaitClose { reg.remove() }
    }

    /** A note, optionally with a photo, for the parent's home screen. Kept 30 days. Free on every plan. */
    suspend fun sendNote(deviceId: String, text: String, photo: Uri?): Boolean = runCatching {
        val uid = me?.uid ?: return false
        val noteId = UUID.randomUUID().toString()
        val data = hashMapOf<String, Any>(
            "fromUid" to uid,
            "fromName" to myFirstName,
            "text" to text.trim().take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "expireAt" to Timestamp(Date(System.currentTimeMillis() + 30L * 86_400_000)),
        )
        if (photo != null) {
            val bytes = withContext(Dispatchers.IO) { compress(photo) } ?: return false
            val path = "notes/$deviceId/$noteId.jpg"
            val ref = FirebaseStorage.getInstance().reference.child(path)
            ref.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
            data["photoUrl"] = ref.downloadUrl.await().toString()
            data["storagePath"] = path
        }
        device(deviceId).collection("notes").document(noteId).set(data).await()
        true
    }.getOrElse { Log.w(TAG, "note", it); false }

    /**
     * A photo for a contact or medicine, chosen on the family phone. Uploaded to
     * media/{deviceId}/{contacts|medicines}/{id}-{time}.jpg (a new name each time, so the phone
     * sees a new URL); the parent's phone downloads its own copy. Old files are removed by the
     * cleanupMedia function.
     * Returns the download URL to save on the contact / medicine.
     */
    suspend fun uploadPhoto(deviceId: String, kind: String, id: String, photo: Uri): String? = runCatching {
        val bytes = withContext(Dispatchers.IO) { compress(photo) } ?: return null
        val ref = FirebaseStorage.getInstance().reference.child("media/$deviceId/$kind/$id-${System.currentTimeMillis()}.jpg")
        ref.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        ref.downloadUrl.await().toString()
    }.getOrElse { Log.w(TAG, "photo", it); null }

    /** Moves a contact up or down the parent's list (the home screen shows them in this order). */
    fun reorderContacts(deviceId: String, ordered: List<com.lusanetech.warmhearth.data.FavoriteContact>) {
        val now = System.currentTimeMillis()
        val batch = fs.batch()
        ordered.forEachIndexed { i, c ->
            if (c.position != i) batch.update(device(deviceId).collection("contacts").document(c.id), mapOf("position" to i, "updatedAt" to now))
        }
        batch.commit()
    }

    /** Last 7 days of watch / band summaries, newest first. */
    fun observeVitalsHistory(deviceId: String): Flow<List<DayVitals>> = callbackFlow {
        val reg = device(deviceId).collection("vitals").orderBy("date", Query.Direction.DESCENDING).limit(7)
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents?.map {
                        DayVitals(
                            date = it.getString("date") ?: it.id,
                            steps = it.getLong("stepsToday"), hrAvg = it.getLong("hrAvg")?.toInt(),
                            hrMin = it.getLong("hrMin")?.toInt(), hrMax = it.getLong("hrMax")?.toInt(),
                            sleepMinutes = it.getLong("sleepMinutes")?.toInt(), spo2 = it.getDouble("spo2"),
                            bpSystolic = it.getLong("bpSystolic")?.toInt(), bpDiastolic = it.getLong("bpDiastolic")?.toInt(),
                            glucose = it.getLong("glucoseMgDl")?.toInt(),
                        )
                    } ?: emptyList(),
                )
            }
        awaitClose { reg.remove() }
    }

    fun observeNotes(deviceId: String): Flow<List<FamilyNote>> = callbackFlow {
        val reg = device(deviceId).collection("notes").orderBy("createdAt", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents?.map {
                        FamilyNote(
                            it.id, it.getString("fromName") ?: "", it.getString("text") ?: "", it.getString("photoUrl"),
                            it.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                            it.getTimestamp("seenAt")?.toDate()?.time,
                        )
                    } ?: emptyList(),
                )
            }
        awaitClose { reg.remove() }
    }

    fun deleteNote(deviceId: String, noteId: String) {
        device(deviceId).collection("notes").document(noteId).delete()
    }

    /**
     * Today's doses on the parent's phone, in *their* time zone (a son in Toronto and a mother in
     * Agartala have different "todays").
     */
    fun observeDoses(deviceId: String, tz: String?, meds: List<Medicine>): Flow<List<DoseStatus>> = callbackFlow {
        val zone = TimeZone.getTimeZone(tz ?: TimeZone.getDefault().id)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(Date())
        val nowHm = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(Date())
        val reg = device(deviceId).collection("doses").whereEqualTo("date", day).addSnapshotListener { snap, _ ->
            val byId = snap?.documents?.associateBy { it.id } ?: emptyMap()
            val list = meds.filter { it.active }.flatMap { m ->
                m.timeList().map { t ->
                    val d = byId["${m.id}_${day}_$t"]
                    val state = when {
                        d?.getString("status") == "taken" -> DoseStatus.State.TAKEN
                        d?.getString("status") == "missed" -> DoseStatus.State.MISSED
                        t <= nowHm -> DoseStatus.State.DUE
                        else -> DoseStatus.State.UPCOMING
                    }
                    DoseStatus(m.id, m.name, t, state, d?.getTimestamp("at")?.toDate()?.time)
                }
            }.sortedBy { it.time }
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    /** Max 1280 px, JPEG 80%, upright. A phone photo goes from ~4 MB to ~250 KB. */
    private fun compress(uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280 || bounds.outHeight / (sample * 2) >= 1280) sample *= 2
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, 1280f / maxOf(raw.width, raw.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }.getOrNull()
}
