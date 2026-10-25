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
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
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
                family.startSeniorSync()
                scheduler.rescheduleAll()
                if (s.fallDetection && s.premium) FallDetectionService.start(app)
            }
            if (s.mode == AppMode.FAMILY) family.registerFamilyToken()
        }
    }
}

val Context.graph: Graph get() = (applicationContext as WarmHearthApp).graph
