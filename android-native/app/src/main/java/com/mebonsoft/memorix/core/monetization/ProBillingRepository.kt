package com.mebonsoft.memorix.core.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface ProBillingRepository {
    val state: StateFlow<ProBillingState>
    fun startConnection()
    fun launchPurchase(activity: Activity)
    fun restorePurchases()
    fun consumeMessage()
}

data class ProBillingState(
    val isReady: Boolean = false,
    val isWorking: Boolean = false,
    val productTitle: String = "Memorix Pro 평생 이용권",
    val productPrice: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

@Singleton
class AndroidProBillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementRepository: ProEntitlementRepository,
) : ProBillingRepository, PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ProBillingState())
    override val state: StateFlow<ProBillingState> = _state
    private var productDetails: ProductDetails? = null

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener(this)
            .build()
    }

    override fun startConnection() {
        if (billingClient.isReady) {
            _state.update { it.copy(isReady = true) }
            queryProductDetails()
            queryExistingPurchases(showRestoredMessage = false)
            return
        }
        _state.update { it.copy(isWorking = true, errorMessage = null) }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { it.copy(isReady = true, isWorking = false) }
                    queryProductDetails()
                    queryExistingPurchases(showRestoredMessage = false)
                } else {
                    _state.update {
                        it.copy(
                            isReady = false,
                            isWorking = false,
                            errorMessage = "결제 연결에 실패했습니다. Google Play에서 앱을 설치한 뒤 다시 시도해 주세요.",
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(isReady = false) }
            }
        })
    }

    override fun launchPurchase(activity: Activity) {
        if (!billingClient.isReady) {
            startConnection()
            _state.update { it.copy(errorMessage = "결제 연결을 준비 중입니다. 잠시 후 다시 눌러주세요.") }
            return
        }
        val details = productDetails
        if (details == null) {
            queryProductDetails()
            _state.update { it.copy(errorMessage = "Memorix Pro 상품 정보를 불러오는 중입니다. 잠시 후 다시 눌러주세요.") }
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun restorePurchases() {
        if (!billingClient.isReady) {
            startConnection()
        }
        queryExistingPurchases(showRestoredMessage = true)
    }

    override fun consumeMessage() {
        _state.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases.orEmpty(), showRestoredMessage = false)
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.update { it.copy(infoMessage = "구매를 취소했습니다.") }
            else -> _state.update { it.copy(errorMessage = result.debugMessage.ifBlank { "구매 처리에 실패했습니다." }) }
        }
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(ProLifetimeProductId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { result, products ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = products.firstOrNull()
                productDetails = details
                _state.update {
                    it.copy(
                        productTitle = details?.title ?: it.productTitle,
                        productPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice,
                    )
                }
            } else {
                _state.update { it.copy(errorMessage = result.debugMessage.ifBlank { "상품 정보를 불러오지 못했습니다." }) }
            }
        }
    }

    private fun queryExistingPurchases(showRestoredMessage: Boolean) {
        if (!billingClient.isReady) return
        _state.update { it.copy(isWorking = true, errorMessage = null) }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases, showRestoredMessage)
            } else {
                _state.update { it.copy(isWorking = false, errorMessage = result.debugMessage.ifBlank { "구매 복원에 실패했습니다." }) }
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, showRestoredMessage: Boolean) {
        val proPurchases = purchases.filter { it.products.contains(ProLifetimeProductId) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (proPurchases.isEmpty()) {
            scope.launch { entitlementRepository.setEntitlement(ProEntitlement.Free) }
            _state.update {
                it.copy(
                    isWorking = false,
                    infoMessage = if (showRestoredMessage) "복원 가능한 Pro 구매 내역이 없습니다." else it.infoMessage,
                )
            }
            return
        }
        proPurchases.forEach { purchase ->
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            } else {
                scope.launch { entitlementRepository.setEntitlement(ProEntitlement.ProLifetime, purchase.purchaseToken) }
            }
        }
        _state.update {
            it.copy(
                isWorking = false,
                infoMessage = if (showRestoredMessage) "Pro 구매 내역을 복원했습니다." else "Memorix Pro가 활성화되었습니다.",
            )
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                scope.launch { entitlementRepository.setEntitlement(ProEntitlement.ProLifetime, purchase.purchaseToken) }
                _state.update { it.copy(infoMessage = "Memorix Pro가 활성화되었습니다.") }
            } else {
                _state.update { it.copy(errorMessage = result.debugMessage.ifBlank { "구매 확인 처리에 실패했습니다." }) }
            }
        }
    }
}
