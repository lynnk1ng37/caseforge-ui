package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 订单详情页页面对象（/home/OrderDetail?sn=）。
 *
 * <p>职责：打开详情、读取状态/单号/金额；不做业务断言。
 */
public class OrderDetailPage {

    private static final Pattern ORDER_SN = Pattern.compile("O\\d{15,}");
    private static final Pattern AMOUNT = Pattern.compile("应付金额[：:]\\s*￥?([\\d,]+\\.\\d{2})");

    private final Page page;
    private final Locator container;
    private final Locator statusCard;
    private final Locator priceBlock;
    private final Locator afterSaleEntry;

    public OrderDetailPage(Page page) {
        this.page = page;
        this.container = LocatorRegistry.resolve(page, "order_detail.container");
        this.statusCard = LocatorRegistry.resolve(page, "order_detail.status_card");
        this.priceBlock = LocatorRegistry.resolve(page, "order_detail.price_block");
        this.afterSaleEntry = LocatorRegistry.resolve(page, "order_detail.after_sale_entry");
    }

    public OrderDetailPage openBySn(String orderSn) {
        RouteRegistry.open(page, "order_detail", Map.of("sn", orderSn));
        assertThat(container.first()).isVisible();
        return this;
    }

    /** 详情页首块卡片的首行即订单状态。 */
    public String status() {
        if (statusCard.count() == 0) {
            return "";
        }
        String text = statusCard.first().innerText().trim();
        return text.isEmpty() ? "" : text.split("\\R")[0].trim();
    }

    public String orderSn() {
        Matcher matcher = ORDER_SN.matcher(container.first().innerText());
        return matcher.find() ? matcher.group() : "";
    }

    public String bodyText() {
        return container.first().innerText();
    }

    public String priceText() {
        return priceBlock.count() == 0 ? "" : priceBlock.first().innerText().trim();
    }

    /** 从「应付金额：￥3,799.00」里解析金额。 */
    public double payableAmount() {
        Matcher matcher = AMOUNT.matcher(bodyText());
        if (!matcher.find()) {
            return -1d;
        }
        return Double.parseDouble(matcher.group(1).replace(",", ""));
    }

    public boolean hasAfterSaleEntry() {
        return afterSaleEntry.count() > 0 && afterSaleEntry.first().isVisible();
    }
}
