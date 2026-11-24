package com.lili.ui.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.lili.ui.config.TestConfig;
import com.lili.ui.flows.PurchaseFlow;
import com.lili.ui.pages.OrderListPage;
import com.lili.ui.pages.PaymentPage;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 买家下单链路 步骤定义（用例层）。
 *
 * <p>人工用例：LILI-ORDER-001 提交订单后订单出现在我的订单-待付款列表。
 *
 * <p>测试目标：验证「加购 → 结算 → 提交」这条链路的用户可见结果，防止
 * 「页面提示成功但订单没生成」这类假成功被漏测。
 *
 * <p>分层约束：本层只允许调用 page object / flow，禁止裸定位、禁止固定等待、
 * 禁止硬编码 URL；业务断言全部集中在本层。
 */
public class OrderSteps {

    private final ScenarioContext ctx;
    private PaymentPage payment;
    private OrderListPage orderList;

    public OrderSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("买家已通过接口登录并注入登录态")
    public void buyerLoggedIn() {
        // 登录与 cookie 注入由 AuthHooks 完成；这里确认前置真的生效，避免场景空跑
        assertThat(ctx.api()).as("API 会话未注入，检查 AuthHooks").isNotNull();
        assertThat(ctx.page()).as("浏览器页面未创建，检查 BrowserHooks").isNotNull();
    }

    @When("买家将商品加入购物车并提交订单")
    public void submitOrder() {
        TestConfig config = TestConfig.get();
        payment = new PurchaseFlow(ctx.page()).submitOrder(config.goodsId(), config.skuId());
        ctx.setTradeSn(payment.tradeSn());
    }

    @Then("收银台应提示订单提交成功")
    public void receiptShowsSuccess() {
        assertThat(payment.successTipText())
                .as("收银台成功文案不符, url=%s", ctx.currentUrl())
                .contains("订单提交成功");
    }

    @And("收银台交易单号应以 T 开头")
    public void tradeSnStartsWithT() {
        assertThat(ctx.tradeSn())
                .as("收银台未从 URL 取到交易单号, url=%s", ctx.currentUrl())
                .startsWith("T");
    }

    @When("买家打开我的订单并切换到待付款")
    public void openMyOrders() {
        orderList = new OrderListPage(ctx.page()).open().switchTo("待付款");
    }

    @Then("待付款标签应处于选中状态")
    public void waitPayTabActive() {
        assertThat(orderList.activeTabText())
                .as("待付款标签未选中, url=%s", ctx.currentUrl())
                .isEqualTo("待付款");
    }

    @And("待付款列表首条应包含商品名与未付款状态")
    public void firstOrderMatches() {
        assertThat(orderList.orderCount())
                .as("待付款列表为空, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(1);

        String firstOrder = orderList.firstOrderText();
        assertThat(firstOrder)
                .as("首条订单未包含目标商品 %s, url=%s", TestConfig.get().goodsName(), ctx.currentUrl())
                .contains(TestConfig.get().goodsName());
        assertThat(firstOrder)
                .as("订单状态应为未付款, url=%s", ctx.currentUrl())
                .contains("未付款");

        List<String> orderSns = orderList.orderSns();
        assertThat(orderSns)
                .as("未从订单卡片解析到 O 开头订单号, url=%s", ctx.currentUrl())
                .isNotEmpty();
        // 登记订单号，AuthHooks 在场景结束后统一取消，避免待付款订单堆积
        ctx.createdOrderSns().add(orderSns.get(0));
    }

    @And("接口待付款列表应能查到该订单")
    public void apiShouldContainOrder() {
        List<String> created = ctx.createdOrderSns();
        assertThat(created).as("未登记订单号，无法做接口回查").isNotEmpty();
        String orderSn = created.get(0);

        List<JsonNode> records = ctx.api().pageOrders("WAIT_PAY");
        assertThat(records)
                .as("接口待付款列表未找到订单 %s, 返回 %d 条", orderSn, records.size())
                .anyMatch(record -> orderSn.equals(record.path("sn").asText()));
    }
}
