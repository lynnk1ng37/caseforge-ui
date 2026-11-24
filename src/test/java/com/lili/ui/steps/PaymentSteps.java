package com.lili.ui.steps;

import com.lili.ui.config.TestConfig;
import com.lili.ui.flows.PurchaseFlow;
import com.lili.ui.pages.OrderListPage;
import com.lili.ui.pages.PaymentPage;
import com.lili.ui.support.DbFixture;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 余额支付链路 步骤定义（用例层）。
 *
 * <p>覆盖人工用例：LLS-ORDER-001。
 * 实测（2026-11-24）：余额支付无需密码，点「余额支付」→ 弹窗确定 → 跳 /payDone。
 */
public class PaymentSteps {

    private final ScenarioContext ctx;
    private PaymentPage payment;

    public PaymentSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("买家从商品详情页立即购买并提交订单")
    public void buyNowAndSubmit() {
        // 余额支付会把钱包扣空（实测 2,906 时收银台不弹确认框），先补到 50,000
        DbFixture.ensureWalletAtLeast(ctx.api().memberId(), 50000d);
        payment = new PurchaseFlow(ctx.page())
                .submitOrder(TestConfig.get().goodsId(), TestConfig.get().skuId());
        ctx.setTradeSn(payment.tradeSn());
        assertThat(payment.successTipText())
                .as("收银台未提示提交成功, url=%s", ctx.currentUrl())
                .contains("订单提交成功");
    }

    @And("买家在收银台使用余额支付")
    public void payWithBalance() {
        payment.payByBalance();
        assertThat(ctx.currentUrl())
                .as("余额支付后未跳转支付完成页, url=%s", ctx.currentUrl())
                .isNotNull();
    }

    @Then("支付完成后应展示支付成功页")
    public void payDoneShown() {
        assertThat(payment.payDoneVisible())
                .as("未展示支付成功页, url=%s", ctx.currentUrl())
                .isTrue();
        assertThat(ctx.currentUrl())
                .as("支付完成页路由不符, url=%s", ctx.currentUrl())
                .contains("/payDone");
    }

    @And("我的订单首条应显示待发货")
    public void firstOrderWaitDeliver() {
        OrderListPage orderList = new OrderListPage(ctx.page()).open();
        assertThat(orderList.orderCount())
                .as("订单列表为空, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(1);
        String first = orderList.firstOrderText();
        assertThat(first)
                .as("支付后首条订单不是待发货, 实际=%s, url=%s", first, ctx.currentUrl())
                .contains("待发货");
        assertThat(orderList.orderSns())
                .as("首条订单未解析到订单号, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }
}
