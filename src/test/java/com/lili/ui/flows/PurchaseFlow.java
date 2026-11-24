package com.lili.ui.flows;

import com.lili.ui.pages.CartPage;
import com.lili.ui.pages.CheckoutPage;
import com.lili.ui.pages.GoodsDetailPage;
import com.lili.ui.pages.PaymentPage;
import com.lili.ui.support.CartWriteRateLimiter;
import com.microsoft.playwright.Page;

/**
 * 购买流程编排：商品详情 → 加入购物车 → 结算 → 提交订单。
 *
 * <p>职责：串联 page 对象并返回下一步需要的事实（收银台页面对象），不做业务断言。
 */
public class PurchaseFlow {

    private final GoodsDetailPage goodsDetail;
    private final CartPage cart;
    private final CheckoutPage checkout;
    private final PaymentPage payment;

    public PurchaseFlow(Page page) {
        this.goodsDetail = new GoodsDetailPage(page);
        this.cart = new CartPage(page);
        this.checkout = new CheckoutPage(page);
        this.payment = new PaymentPage(page);
    }

    public CartPage addToCart(String goodsId, String skuId) {
        // 后端对加购有短窗口限流（code=1003），连续加购会静默失败成「点了没反应」
        CartWriteRateLimiter.awaitSlot();
        goodsDetail.open(goodsId, skuId).addToCart();
        return cart.open();
    }

    /** 完整下单链路，返回已提交的收银台页面对象。 */
    public PaymentPage submitOrder(String goodsId, String skuId) {
        addToCart(goodsId, skuId);
        cart.goCheckout();
        checkout.expectLoaded();
        checkout.submit();
        payment.expectSubmitted();
        return payment;
    }

    public GoodsDetailPage goodsDetail() {
        return goodsDetail;
    }
}
