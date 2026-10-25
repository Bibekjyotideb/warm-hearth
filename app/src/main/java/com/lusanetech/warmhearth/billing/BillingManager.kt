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
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.data.Prefs
import com.lusanetech.warmhearth.family.FamilyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * One non-consumable in-app product: "premium_lifetime".
 * Create it in Play Console > Monetize > In-app products with the same id, then set prices
 * per country (e.g. US $9.99, India ₹399).
 *
 * Written against Play Billing Library 8. If Android Studio flags an API here after a
 * library upgrade, it is a small rename; the flow stays the same.
 *
 * TODO before scaling: verify purchase tokens server-side (Cloud Function + Play Developer API)
 * instead of trusting the client when it marks a device premium.
 */
class BillingManager(
    context: Context,
    private val prefs: Prefs,
    private val family: FamilyRepository,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "premium_lifetime"
        private const val TAG = "Billing"
    }

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var details: ProductDetails? = null
    private var connecting = false
    /** When a family member buys for a parent's phone, the unlock is applied to that device. */
    private var buyingForDevice: String? = null

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun connect() {
        if (client.isReady || connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { loadProduct(); restore() }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
            }
        })
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            ).build()
        val list = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
            client.queryProductDetailsAsync(params) { _, result ->
                if (cont.isActive) cont.resume(result.productDetailsList)
            }
        }
        details = list.firstOrNull()
        _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    /** Re-applies a purchase after reinstall or on a new phone with the same Google account. */
    suspend fun restore() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val purchases = suspendCancellableCoroutine<List<Purchase>> { cont ->
            client.queryPurchasesAsync(params) { _, list -> if (cont.isActive) cont.resume(list) }
        }
        purchases.filter { PRODUCT_ID in it.products }.forEach { handle(it) }
    }

    /** Opens Google Play's purchase sheet. [forDeviceId] is set when buying from the family phone. */
    fun buy(activity: Activity, forDeviceId: String? = null) {
        _error.value = null
        val d = details
        if (d == null) {
            connect()
            _error.value = "not_ready"
            return
        }
        buyingForDevice = forDeviceId
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        client.launchBillingFlow(activity, flow)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { p -> scope.launch { handle(p) } }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { restore() }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _error.value = result.debugMessage.ifBlank { "error" }
        }
    }

    private suspend fun handle(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED || PRODUCT_ID !in p.products) return
        if (!p.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            suspendCancellableCoroutine { cont -> client.acknowledgePurchase(ack) { if (cont.isActive) cont.resume(Unit) } }
        }
        prefs.setPremium(true)
        when (prefs.current().mode) {
            AppMode.SENIOR -> family.markOwnDevicePremium()
            AppMode.FAMILY -> buyingForDevice?.let { family.setDevicePremium(it) }
            AppMode.UNSET -> Unit
        }
        buyingForDevice = null
    }
}
