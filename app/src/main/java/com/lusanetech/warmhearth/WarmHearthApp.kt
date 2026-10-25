package com.lusanetech.warmhearth

import android.app.Application
import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.lusanetech.warmhearth.billing.BillingManager
import com.lusanetech.warmhearth.data.AppDatabase
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.data.Prefs
import com.lusanetech.warmhearth.fall.FallDetectionService
import com.lusanetech.warmhearth.family.FamilyRepository
import com.lusanetech.warmhearth.meds.MedicineScheduler
import com.lusanetech.warmhearth.util.Notifications
import com.lusanetech.warmhearth.util.RemoteFlags
import com.lusanetech.warmhearth.util.Telemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WarmHearthApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        // Only genuine copies of the app from Play can call Firebase AI / Firestore.
        // Debug builds use the debug provider: copy the token it logs into Firebase console → App Check → Manage debug tokens.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        RemoteFlags.init()
        Notifications.createChannels(this)
        graph = Graph(this)
        graph.start()
    }
}

/** Tiny manual dependency container; the app is small enough not to need Hilt. */
class Graph(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.create(app)
    val prefs = Prefs(app)
    val scheduler = MedicineScheduler(app, db)
    val family = FamilyRepository(app, db, prefs, scheduler, scope)
    val billing = BillingManager(app, prefs, family, scope)

    fun start() {
        scope.launch {
            prefs.ensureDeviceId()
            billing.connect()
            val s = prefs.current()
            if (s.mode == AppMode.SENIOR) {
                family.uid() // anonymous account for this phone
                family.startSeniorSync()
                scheduler.rescheduleAll()
                Telemetry.heartbeat(app, "devices", prefs.ensureDeviceId())
                if (s.fallDetection && s.premium && RemoteFlags.fallDetectionEnabled) FallDetectionService.start(app)
                if (!RemoteFlags.fallDetectionEnabled) FallDetectionService.stop(app) // remote kill switch
            }
            if (s.mode == AppMode.FAMILY) {
                family.registerFamilyToken() // also the daily heartbeat; no-op until signed in with Google
                family.watchFamilyPremium()
            }
        }
    }
}

val Context.graph: Graph get() = (applicationContext as WarmHearthApp).graph
