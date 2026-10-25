package com.lusanetech.warmhearth.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.functions.FirebaseFunctions
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.data.Prefs
import com.lusanetech.warmhearth.family.FamilyRepository
import com.lusanetech.warmhearth.util.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

/** One plan as shown on the paywall, with the price Google Play gives for this user's country. */
data class Plan(
    val basePlanId: String,        // "monthly" | "yearly"
    val offerToken: String,
    val price: String,             // recurring price, e.g. "₹899" or "$89.00"
    val priceMicros: Long,
    val hasFreeTrial: Boolean,     // only offered to people who never had a trial
)

/**
 * WarmHearth Premium is a Google Play subscription: product "warmhearth_premium" with base plans
 * "monthly" and "yearly", each with a 7-day free-trial offer ("free-trial-7d").
 * Prices are set per country in Play Console (India ₹149 / ₹899 a month / year, elsewhere US$4.99 / US$39.99); the app
 * always shows what Play returns, so every user sees their own currency.
 *
 * Premium is granted by the server: each purchase token goes to the verifyPurchase Cloud Function,
 * which checks it with Google Play. Renewals, cancellations and refunds are handled server-side.
 *
 * Written against Play Billing Library 8. If Android Studio flags an API after a library upgrade,
 * it's a small rename; the flow stays the same.
 */
class BillingManager(
    private val context: Context,
    private val prefs: Prefs,
    private val family: FamilyRepository,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "warmhearth_premium"
        const val MANAGE_URL = "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=com.lusanetech.warmhearth"
        private const val TAG = "Billing"
    }

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .build()

    private var details: ProductDetails? = null
    private var connecting = false
    private var buyingForDevice: String? = null

    /** Linked parent phones to unlock when the family member owns the subscription (set by FamilyApp). */
    var familyDeviceIds: List<String> = emptyList()

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun connect() {
        if (client.isReady || connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { loadPlans(); restore() }
                else Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
            }
            override fun onBillingServiceDisconnected() { connecting = false }
        })
    }

    private suspend fun loadPlans() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()),
        ).build()
        val list = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
            client.queryProductDetailsAsync(params) { _, result -> if (cont.isActive) cont.resume(result.productDetailsList) }
        }
        val d = list.firstOrNull() ?: return
        details = d
        // For each base plan prefer the trial offer (Play only returns it if this user is eligible).
        _plans.value = (d.subscriptionOfferDetails ?: emptyList())
            .groupBy { it.basePlanId }
            .mapNotNull { (basePlan, offers) ->
                val chosen = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } } ?: offers.firstOrNull { it.offerId == null } ?: offers.first()
                val recurring = chosen.pricingPhases.pricingPhaseList.last()
                Plan(
                    basePlanId = basePlan,
                    offerToken = chosen.offerToken,
                    price = recurring.formattedPrice,
                    priceMicros = recurring.priceAmountMicros,
                    hasFreeTrial = chosen.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L },
                )
            }
            .sortedByDescending { it.basePlanId == "yearly" }
    }

    /** Re-applies an active subscription after reinstall, on a new phone, or to newly linked phones. */
    suspend fun restore() {
        if (!client.isReady) { connect(); return }
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val purchases = suspendCancellableCoroutine<List<Purchase>> { cont ->
            client.queryPurchasesAsync(params) { _, list -> if (cont.isActive) cont.resume(list) }
        }
        purchases.filter { PRODUCT_ID in it.products }.forEach { handle(it) }
    }

    /** Opens Google Play's subscription sheet. [forDeviceId] is set when buying from the family phone. */
    fun buy(activity: Activity, plan: Plan, forDeviceId: String? = null) {
        _error.value = null
        val d = details ?: run { connect(); _error.value = "not_ready"; return }
        buyingForDevice = forDeviceId
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(plan.offerToken).build()),
            ).build()
        Telemetry.event(context, "paywall_buy_tap", "plan" to plan.basePlanId, "trial" to plan.hasFreeTrial)
        client.launchBillingFlow(activity, flow)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { p -> scope.launch { handle(p) } }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { restore() }
            BillingClient.BillingResponseCode.USER_CANCELED -> Telemetry.event(context, "paywall_cancel")
            else -> _error.value = result.debugMessage.ifBlank { "error" }
        }
    }

    private suspend fun handle(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED || PRODUCT_ID !in p.products) return
        val s = prefs.current()
        val devices = when (s.mode) {
            AppMode.SENIOR -> listOf(s.deviceId)
            AppMode.FAMILY -> (listOfNotNull(buyingForDevice) + familyDeviceIds).distinct()
            AppMode.UNSET -> emptyList()
        }.filter { it.isNotBlank() }
        val ok = runCatching {
            family.uid()
            FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("verifyPurchase")
                .call(mapOf("purchaseToken" to p.purchaseToken, "productId" to PRODUCT_ID, "deviceIds" to devices))
                .await()
        }.onFailure { Log.w(TAG, "verify failed", it); _error.value = "verify" }.isSuccess

        if (ok) {
            prefs.setPremium(true)
            Telemetry.event(context, "subscription_verified", "mode" to s.mode.name)
        } else if (!p.isAcknowledged) {
            // Server unreachable: acknowledge so Google doesn't auto-refund after 3 days.
            // Premium is applied on the next successful restore() / app start.
            val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            suspendCancellableCoroutine { cont -> client.acknowledgePurchase(ack) { if (cont.isActive) cont.resume(Unit) } }
        }
        buyingForDevice = null
    }
}
