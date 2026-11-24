package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 结算页（/pay，填写订单信息）页面对象。
 *
 * <p>定位器来源：classpath:/locators/checkout.yaml
 */
public class CheckoutPage {

    private final Page page;
    private final Locator pageFlag;
    private final Locator selectedAddress;
    private final Locator submitButton;
    private final Locator goodsItem;
    private final Locator goodsPrice;

    public CheckoutPage(Page page) {
        this.page = page;
        this.pageFlag = LocatorRegistry.resolve(page, "checkout.page_flag");
        this.selectedAddress = LocatorRegistry.resolve(page, "checkout.address_selected");
        this.submitButton = LocatorRegistry.resolve(page, "checkout.submit");
        this.goodsItem = LocatorRegistry.resolve(page, "checkout.goods_item");
        this.goodsPrice = LocatorRegistry.resolve(page, "checkout.goods_price");
    }

    public String goodsItemText() {
        return goodsItem.count() == 0 ? "" : goodsItem.first().innerText().trim();
    }

    public String goodsPriceText() {
        return goodsPrice.count() == 0 ? "" : goodsPrice.first().innerText().trim();
    }

    public CheckoutPage expectLoaded() {
        assertThat(page).hasURL(Pattern.compile("/pay"));
        assertThat(pageFlag.first()).isVisible();
        assertThat(submitButton).isVisible();
        return this;
    }

    public String totalGoodsText() {
        return LocatorRegistry.resolve(page, "checkout.total_goods").first().innerText().trim();
    }

    public String payableText() {
        return LocatorRegistry.resolve(page, "checkout.payable").first().innerText().trim();
    }

    /** 应付金额数值。 */
    public double payableAmount() {
        Locator amount = LocatorRegistry.resolve(page, "checkout.payable_amount");
        if (amount.count() == 0) {
            return -1d;
        }
        String digits = amount.first().innerText().replaceAll("[^\\d.]", "");
        return digits.isEmpty() ? -1d : Double.parseDouble(digits);
    }

    /** 等应付金额刷新到指定值（选券后金额是异步重算）。 */
    public CheckoutPage awaitPayable(double expected) {
        Locator amount = LocatorRegistry.resolve(page, "checkout.payable_amount");
        assertThat(amount.first()).containsText(String.format("%,.2f", expected));
        return this;
    }

    /** 使用第一张可用优惠券（结算页点券的「立即使用」）。 */
    public CheckoutPage chooseFirstCoupon() {
        Locator use = LocatorRegistry.resolve(page, "coupon.use_btn");
        use.first().click();
        return this;
    }

    public boolean hasDefaultAddress() {
        return selectedAddress.count() > 0;
    }

    public CheckoutPage submit() {
        submitButton.click();
        return this;
    }
}
