package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 收银台页（提交订单成功后）页面对象。
 *
 * <p>定位器来源：classpath:/locators/payment.yaml
 */
public class PaymentPage {

    private final Page page;
    private final Locator successTip;
    private final Locator amountLabel;

    public PaymentPage(Page page) {
        this.page = page;
        this.successTip = LocatorRegistry.resolve(page, "payment.success_tip");
        this.amountLabel = LocatorRegistry.resolve(page, "payment.amount_label");
    }

    public PaymentPage expectSubmitted() {
        assertThat(successTip.first()).isVisible();
        assertThat(amountLabel.first()).isVisible();
        return this;
    }

    /** 收银台成功文案，供 steps 层做业务断言（page 层只提供取值）。 */
    public String successTipText() {
        return successTip.first().innerText().trim();
    }

    /**
     * 从收银台 URL 的 {@code sn} 参数取交易单号（T 开头）。
     *
     * <p>注意与订单号区分：交易单号 T 用于收银台查询，订单号 O 用于取消与列表回查。
     */
    /** 余额支付：点「余额支付」→ 确认弹窗点「确定」（实测无需支付密码）。 */
    public PaymentPage payByBalance() {
        // 页面刚跳过来时支付方式区还没渲染，先等收银台就绪
        assertThat(successTip.first()).isVisible();
        Locator option = LocatorRegistry.resolve(page, "payment.balance_option");
        Locator confirm = LocatorRegistry.resolve(page, "payment.confirm_ok");
        option.first().click();
        try {
            confirm.first().click(new Locator.ClickOptions().setTimeout(10000));
        } catch (com.microsoft.playwright.PlaywrightException e) {
            // 首次点击可能被页面重渲染吞掉，重试一次
            option.first().click();
            confirm.first().click(new Locator.ClickOptions().setTimeout(15000));
        }
        return this;
    }

    /** 支付完成页（/payDone）是否已展示。 */
    public boolean payDoneVisible() {
        Locator flag = LocatorRegistry.resolve(page, "payment.done_flag");
        try {
            flag.first().waitFor(new Locator.WaitForOptions().setTimeout(10000));
        } catch (com.microsoft.playwright.PlaywrightException ignored) {
            return false;
        }
        return flag.count() > 0 && flag.first().isVisible();
    }

    public String tradeSn() {
        String url = page.url();
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return "";
        }
        for (String pair : url.substring(queryStart + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "sn".equals(pair.substring(0, eq))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
