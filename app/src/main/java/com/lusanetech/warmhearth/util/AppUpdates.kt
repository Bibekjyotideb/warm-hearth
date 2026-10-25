package com.lusanetech.warmhearth.util

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.lusanetech.warmhearth.BuildConfig

/**
 * Keeps phones on a working version without anyone having to open the Play Store:
 *
 *  - Below Remote Config "min_version_code" → IMMEDIATE update (full-screen, can't be skipped).
 *    Use this when an old version is broken (e.g. a Play Billing or Firebase API change).
 *  - Below "recommended_version_code", or an update has waited 3+ days → FLEXIBLE update:
 *    downloads in the background and installs the next time the app is closed.
 *  - Seniors never see a Play Store page they could get stuck in.
 */
class AppUpdates(private val activity: ComponentActivity) {
    private val manager = AppUpdateManagerFactory.create(activity)
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) manager.completeUpdate()
    }

    fun check() {
        manager.registerListener(listener)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val current = BuildConfig.VERSION_CODE.toLong()
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> manager.completeUpdate()
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
                info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE -> Unit
                current < RemoteFlags.minVersionCode && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
                (current < RemoteFlags.recommendedVersionCode || (info.clientVersionStalenessDays() ?: 0) >= 3) &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
            }
        }
    }

    fun release() = manager.unregisterListener(listener)

    companion object {
        /**
         * Asks for a Play rating at a happy moment (Google decides whether to actually show it,
         * and limits how often). Called on the family phone once a parent's phone has been linked
         * for a few days: those users have seen the value.
         */
        fun maybeAskForReview(activity: ComponentActivity) {
            val sp = activity.getSharedPreferences("review", 0)
            if (sp.getBoolean("asked", false)) return
            val rm = ReviewManagerFactory.create(activity)
            rm.requestReviewFlow().addOnSuccessListener { info ->
                rm.launchReviewFlow(activity, info).addOnCompleteListener { sp.edit().putBoolean("asked", true).apply() }
            }
        }
    }
}
