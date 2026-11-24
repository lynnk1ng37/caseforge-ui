package com.lili.ui.steps;

import com.lili.ui.config.TestConfig;
import com.lili.ui.pages.AfterSalePage;
import com.lili.ui.pages.CartPage;
import com.lili.ui.pages.CheckoutPage;
import com.lili.ui.pages.OrderDetailPage;
import com.lili.ui.pages.OrderListPage;
import com.lili.ui.support.CartFixture;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单中心与结算金额 步骤定义（用例层）。
 *
 * <p>覆盖人工用例：LLS-UC-001 / 002 / 003 / 004 / 005 / 010、LLS-ORDER-010。
 */
public class OrderCenterSteps {

    private static final Pattern TIME = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})");
    private static final Pattern AMOUNT = Pattern.compile("￥\\s*([\\d,]+\\.\\d{2})");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ScenarioContext ctx;
    private OrderListPage orderList;
    private OrderDetailPage orderDetail;
    private String firstSn = "";

    public OrderCenterSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("买家打开我的订单")
    public void openMyOrders() {
        orderList = new OrderListPage(ctx.page()).open();
        assertThat(orderList.tabCount())
                .as("订单页签不足 4 个, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(4);
    }

    @Then("全部订单标签应处于选中状态")
    public void allTabActive() {
        assertThat(orderList.activeTabText())
                .as("默认未停在全部订单, 实际=%s, url=%s", orderList.activeTabText(), ctx.currentUrl())
                .contains("全部订单");
    }

    @And("订单数量应大于等于 1")
    public void orderCountAtLeastOne() {
        assertThat(orderList.orderCount())
                .as("全部订单列表为空, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(1);
    }

    @And("订单号应按创建时间倒序")
    public void ordersSortedByTimeDesc() {
        List<LocalDateTime> times = new ArrayList<>();
        for (String text : orderList.orderTexts()) {
            Matcher matcher = TIME.matcher(text);
            if (matcher.find()) {
                times.add(LocalDateTime.parse(matcher.group(1), FMT));
            }
        }
        assertThat(times)
                .as("订单卡片未解析到创建时间, url=%s", ctx.currentUrl())
                .isNotEmpty();
        List<LocalDateTime> sorted = new ArrayList<>(times);
        sorted.sort(Comparator.reverseOrder());
        assertThat(times)
                .as("订单未按创建时间倒序, 实际=%s, url=%s", times, ctx.currentUrl())
                .isEqualTo(sorted);
    }

    @When("买家切换到待付款标签")
    public void switchToWaitPay() {
        orderList.switchTo("待付款");
        assertThat(orderList.activeTabText())
                .as("切换后待付款标签未选中, url=%s", ctx.currentUrl())
                .contains("待付款");
    }

    @When("买家切换到已完成标签")
    public void switchToCompleted() {
        orderList.switchTo("已完成");
        assertThat(orderList.activeTabText())
                .as("切换后已完成标签未选中, url=%s", ctx.currentUrl())
                .contains("已完成");
    }

    @Then("待付款标签下的订单状态文案应一致")
    public void waitPayRowsAllUnpaid() {
        assertThat(orderList.activeTabText())
                .as("待付款标签未选中, url=%s", ctx.currentUrl())
                .contains("待付款");
        assertThat(orderList.orderTexts())
                .as("待付款列表出现了非未付款订单, url=%s", ctx.currentUrl())
                .allMatch(text -> text.contains("未付款"));
    }

    @Then("已完成标签下的订单状态文案应一致")
    public void completedRowsAllCompleted() {
        // 标签切换后列表异步刷新：最多重试 3 次（每次重新点标签触发刷新）
        boolean allCompleted = false;
        for (int attempt = 0; attempt < 3 && !allCompleted; attempt++) {
            orderList.switchTo("已完成");
            allCompleted = orderList.orderTexts().stream().allMatch(text -> text.contains("已完成"));
        }
        assertThat(orderList.activeTabText())
                .as("已完成标签未选中, url=%s", ctx.currentUrl())
                .contains("已完成");
        assertThat(allCompleted)
                .as("已完成列表出现了非已完成订单, 实际=%s, url=%s", orderList.orderTexts(), ctx.currentUrl())
                .isTrue();
    }

    @When("买家连续切换待付款、待收货与已完成标签")
    public void switchTabsQuickly() {
        orderList.switchTo("待付款").switchTo("待收货").switchTo("已完成");
        assertThat(orderList.activeTabText())
                .as("连续切换后未停在已完成标签, url=%s", ctx.currentUrl())
                .contains("已完成");
    }

    @Then("当前选中标签应为已完成")
    public void activeTabIsCompleted() {
        assertThat(orderList.activeTabText())
                .as("连续切换后选中标签不符, 实际=%s, url=%s", orderList.activeTabText(), ctx.currentUrl())
                .contains("已完成");
    }

    @And("当前列表的订单状态应与已完成的标签一致")
    public void currentRowsMatchCompleted() {
        assertThat(orderList.orderTexts())
                .as("连续切换后列表残留上一个标签的数据, url=%s", ctx.currentUrl())
                .allMatch(text -> text.contains("已完成"));
    }

    @When("买家按列表第一笔订单号搜索")
    public void searchFirstOrderSn() {
        List<String> sns = orderList.orderSns();
        assertThat(sns)
                .as("未从列表解析到订单号, url=%s", ctx.currentUrl())
                .isNotEmpty();
        firstSn = sns.get(0);
        orderList.searchBySn(firstSn).awaitOrderCount(1);
    }

    @Then("搜索结果应只包含该订单号")
    public void searchResultOnlyThatOrder() {
        List<String> sns = orderList.orderSns();
        assertThat(sns)
                .as("搜索结果不是 1 条该订单, 期望=%s 实际=%s, url=%s", firstSn, sns, ctx.currentUrl())
                .containsExactly(firstSn);
    }

    @When("买家搜索不存在的订单号")
    public void searchNotExistSn() {
        orderList.searchBySn("O9999999999999999").awaitOrderCount(0);
        assertThat(orderList.orderCount())
                .as("不存在的订单号仍返回结果, url=%s", ctx.currentUrl())
                .isZero();
    }

    @Then("订单列表应展示空态")
    public void orderListEmpty() {
        assertThat(orderList.orderCount())
                .as("不存在的订单号仍有结果, url=%s", ctx.currentUrl())
                .isZero();
    }

    @When("买家打开我的订单并进入第一笔订单详情")
    public void openFirstOrderDetail() {
        orderList = new OrderListPage(ctx.page()).open();
        List<String> sns = orderList.orderSns();
        assertThat(sns)
                .as("订单列表为空, 无法打开详情, url=%s", ctx.currentUrl())
                .isNotEmpty();
        firstSn = sns.get(0);
        orderList.openFirstDetail();
        orderDetail = new OrderDetailPage(ctx.page());
    }

    @Then("详情订单号应与列表一致")
    public void detailSnMatchesList() {
        assertThat(orderDetail.orderSn())
                .as("详情订单号与列表不一致, 列表=%s, url=%s", firstSn, ctx.currentUrl())
                .isEqualTo(firstSn);
    }

    @And("详情应展示应付金额与商品名")
    public void detailShowsAmountAndGoods() {
        assertThat(orderDetail.bodyText())
                .as("详情未展示目标商品 %s, url=%s", TestConfig.get().goodsName(), ctx.currentUrl())
                .contains(TestConfig.get().goodsName());
        assertThat(orderDetail.payableAmount())
                .as("详情应付金额未解析, 实际=%.2f, url=%s", orderDetail.payableAmount(), ctx.currentUrl())
                .isGreaterThan(0d);
    }

    @When("买家打开已关闭订单的详情")
    public void openCancelledOrderDetail() {
        orderList = new OrderListPage(ctx.page()).open();
        List<String> texts = orderList.orderTexts();
        List<String> sns = orderList.orderSns();
        String cancelledSn = "";
        for (int i = 0; i < texts.size() && i < sns.size(); i++) {
            if (texts.get(i).contains("已关闭")) {
                cancelledSn = sns.get(i);
                break;
            }
        }
        assertThat(cancelledSn)
                .as("列表里没有已关闭订单, 无法验证售后入口, url=%s", ctx.currentUrl())
                .isNotEmpty();
        firstSn = cancelledSn;
        orderDetail = new OrderDetailPage(ctx.page()).openBySn(cancelledSn);
    }

    @Then("该订单不应展示申请售后入口")
    public void noAfterSaleEntry() {
        assertThat(orderDetail.status())
                .as("订单状态不是已关闭, 实际=%s, url=%s", orderDetail.status(), ctx.currentUrl())
                .contains("已关闭");
        assertThat(orderDetail.hasAfterSaleEntry())
                .as("已关闭订单仍展示「申请售后」, url=%s", ctx.currentUrl())
                .isFalse();
    }

    @When("买家打开我的售后")
    public void openAfterSaleList() {
        AfterSalePage afterSale = new AfterSalePage(ctx.page()).open();
        assertThat(afterSale.emptyTipVisible())
                .as("售后列表未展示空态, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @Then("售后列表应展示空态或按状态展示")
    public void afterSaleListOk() {
        assertThat(ctx.currentUrl())
                .as("售后页路由不符, url=%s", ctx.currentUrl())
                .contains("/home/AfterSale");
    }

    @When("买家从购物车进入结算页")
    public void goCheckoutFromCart() {
        CartFixture.seed(ctx.api(), CartFixture.mainGoods(1));
        new CartPage(ctx.page()).open().goCheckout();
        new CheckoutPage(ctx.page()).expectLoaded();
        assertThat(ctx.currentUrl())
                .as("未进入结算页, url=%s", ctx.currentUrl())
                .contains("/pay");
    }

    @Then("结算页应付金额应等于商品总额")
    public void payableEqualsGoodsTotal() {
        CheckoutPage checkout = new CheckoutPage(ctx.page());
        // 单件商品、未选券与礼品卡、运费 0：应付金额应等于商品单价
        double payable = checkout.payableAmount();
        assertThat(payable)
                .as("应付金额未解析或与商品单价不一致, 应付=%.2f 期望=%.2f, url=%s",
                        payable, TestConfig.get().goodsPrice(), ctx.currentUrl())
                .isEqualTo(TestConfig.get().goodsPrice());
    }

    @And("结算页不应出现负数金额")
    public void payableNotNegative() {
        CheckoutPage checkout = new CheckoutPage(ctx.page());
        assertThat(checkout.payableAmount())
                .as("应付金额为负, 应付=%.2f, url=%s", checkout.payableAmount(), ctx.currentUrl())
                .isGreaterThanOrEqualTo(0d);
    }

    // ---------- 第二批：取消订单 / 重复提交 ----------

    private String pendingSn = "";

    @io.cucumber.java.en.Given("买家有一笔待付款订单")
    public void createPendingOrder() {
        new com.lili.ui.flows.PurchaseFlow(ctx.page())
                .submitOrder(TestConfig.get().goodsId(), TestConfig.get().skuId());
        orderList = new OrderListPage(ctx.page()).open().switchTo("待付款");
        assertThat(orderList.orderCount())
                .as("待付款列表为空, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(1);
        pendingSn = orderList.orderSns().get(0);
        assertThat(pendingSn)
                .as("未解析到待付款订单号, url=%s", ctx.currentUrl())
                .startsWith("O");
    }

    @When("买家在待付款列表取消该订单")
    public void cancelPendingOrder() {
        orderList.cancelFirstOrder();
        assertThat(ctx.currentUrl())
                .as("取消后离开了订单页, url=%s", ctx.currentUrl())
                .contains("/home/MyOrder");
    }

    @Then("该订单状态应为已取消")
    public void pendingOrderCancelled() {
        // 取消是异步请求：刷新订单页推进时间，最多轮询 4 次
        boolean cancelled = false;
        for (int attempt = 0; attempt < 4 && !cancelled; attempt++) {
            orderList = new OrderListPage(ctx.page()).open();
            cancelled = ctx.api().pageOrders("ALL").stream()
                    .anyMatch(order -> pendingSn.equals(order.path("sn").asText())
                            && "CANCELLED".equals(order.path("orderStatus").asText()));
        }
        assertThat(cancelled)
                .as("订单 %s 状态不是 CANCELLED, url=%s", pendingSn, ctx.currentUrl())
                .isTrue();
        boolean stillPending = ctx.api().pageOrders("WAIT_PAY").stream()
                .anyMatch(order -> pendingSn.equals(order.path("sn").asText()));
        assertThat(stillPending)
                .as("订单 %s 仍在待付款列表, url=%s", pendingSn, ctx.currentUrl())
                .isFalse();
    }

    @When("买家在结算页连续点击提交订单 3 次")
    public void submitOrderThreeTimes() {
        baselineSns = new java.util.HashSet<>();
        ctx.api().pageOrders("WAIT_PAY").forEach(order -> baselineSns.add(order.path("sn").asText()));
        pendingBefore = baselineSns.size();
        CartFixture.seed(ctx.api(), CartFixture.mainGoods(1));
        new CartPage(ctx.page()).open().goCheckout();
        CheckoutPage checkout = new CheckoutPage(ctx.page()).expectLoaded();
        for (int i = 0; i < 3; i++) {
            try {
                checkout.submit();
            } catch (RuntimeException ignored) {
                // 第一次提交后页面已跳到收银台，后续点击会因元素消失抛错，属预期
            }
        }
        assertThat(ctx.currentUrl())
                .as("连续提交后未进入收银台, url=%s", ctx.currentUrl())
                .contains("/payment");
    }

    private int pendingBefore = -1;
    private java.util.Set<String> baselineSns = new java.util.HashSet<>();

    @Then("待付款订单只应新增 1 笔")
    public void onlyOneOrderCreated() {
        // 新建订单进入待付款列表是异步的：刷新订单页推进时间，最多轮询 4 次
        java.util.Set<String> created = new java.util.HashSet<>();
        for (int attempt = 0; attempt < 4 && created.size() < 1; attempt++) {
            new OrderListPage(ctx.page()).open();
            created.clear();
            ctx.api().pageOrders("WAIT_PAY").forEach(order -> {
                String sn = order.path("sn").asText();
                if (!baselineSns.contains(sn)) {
                    created.add(sn);
                }
            });
        }
        assertThat(created)
                .as("重复提交新增订单数不是 1, 新增=%s, 之前=%d, url=%s", created, pendingBefore, ctx.currentUrl())
                .hasSize(1);
        // 登记新订单，场景结束后由 AuthHooks 取消，避免待付款订单堆积
        created.forEach(ctx.createdOrderSns()::add);
    }

    // ---------- 优惠券门槛（ORDER-006） ----------

    private static final String CHEAP_SKU = "1381792103876526080";
    private static final String CHEAP_GOODS = "1377060105891086336";
    private static final String PRICEY_SKU = "1377132926088511488";
    private static final String PRICEY_GOODS = "1377132926042374144";
    private static final String STORE_ID = "1376433565247471616";

    @io.cucumber.java.en.Given("买家购物车放入金额 69 元的商品")
    public void cartWithCheapGoods() {
        CartFixture.seed(ctx.api(), new CartFixture.Item(CHEAP_SKU, CHEAP_GOODS,
                "苏宁超市自营伊利 安慕希希腊风味酸奶", 69.00, STORE_ID, "家家乐", 1));
        new CartPage(ctx.page()).open().goCheckout();
        new CheckoutPage(ctx.page()).expectLoaded();
        assertThat(ctx.currentUrl())
                .as("未进入结算页, url=%s", ctx.currentUrl())
                .contains("/pay");
    }

    @Then("结算页应付金额应为 69 元")
    public void payableIs69() {
        double payable = new CheckoutPage(ctx.page()).awaitPayable(69.00).payableAmount();
        assertThat(payable)
                .as("不满券门槛却发生抵扣, 应付=%.2f, url=%s", payable, ctx.currentUrl())
                .isEqualTo(69.00);
    }

    @io.cucumber.java.en.When("买家购物车换成金额 222 元的商品并进入结算页")
    public void cartWithPriceyGoods() {
        CartFixture.seed(ctx.api(), new CartFixture.Item(PRICEY_SKU, PRICEY_GOODS,
                "纳斯（NARS) Blush炫色腮红/胭脂 超级经典高潮 定妆提亮", 222.00, STORE_ID, "家家乐", 1));
        new CartPage(ctx.page()).open().goCheckout();
        new CheckoutPage(ctx.page()).expectLoaded();
        assertThat(new CheckoutPage(ctx.page()).payableAmount())
                .as("未选券时应付应等于商品金额, url=%s", ctx.currentUrl())
                .isEqualTo(222.00);
    }

    @io.cucumber.java.en.And("买家在结算页选择第一张可用优惠券")
    public void chooseFirstCoupon() {
        new CheckoutPage(ctx.page()).chooseFirstCoupon();
        assertThat(ctx.currentUrl())
                .as("选券后离开了结算页, url=%s", ctx.currentUrl())
                .contains("/pay");
    }

    @Then("结算页应付金额应为 212 元")
    public void payableIs212() {
        double payable = new CheckoutPage(ctx.page()).awaitPayable(212.00).payableAmount();
        assertThat(payable)
                .as("满减券未按 10 元抵扣, 应付=%.2f, url=%s", payable, ctx.currentUrl())
                .isEqualTo(212.00);
    }

    private static double amountOf(String text) {
        Matcher matcher = AMOUNT.matcher(text == null ? "" : text);
        double last = -1d;
        while (matcher.find()) {
            last = Double.parseDouble(matcher.group(1).replace(",", ""));
        }
        return last;
    }
}
