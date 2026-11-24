package com.lili.ui.steps;

import com.lili.ui.config.TestConfig;
import com.lili.ui.support.CartFixture;
import com.lili.ui.support.CartWriteRateLimiter;
import com.lili.ui.support.Session;

import java.util.List;
import com.lili.ui.pages.CartPage;
import com.lili.ui.pages.GoodsDetailPage;
import com.lili.ui.pages.HomePage;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 购物车 步骤定义（用例层）。
 *
 * <p>覆盖人工用例：LLS-CART-001 / 002 / 004 / 005 / 006 / 007 / 009 / 010。
 *
 * <p>实测行为（2026-11-23）：数量组件上限为 99；输入 0 / -1 / abc 回退为 1；
 * 输入 999999 时输入框保留但总价不重算；清空购物车弹确认框。
 */
public class CartSteps {

    private static final double PRICE = 3799.00;
    private static final double PRICE2 = 1.00;

    private final ScenarioContext ctx;
    private CartPage cart;
    private int stockLimit = -1;
    private String quantityBeforePlus = "";

    public CartSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("买家购物车为空")
    public void emptyCart() {
        CartFixture.clear(ctx.api());
        cart = new CartPage(ctx.page()).open();
        assertThat(cart.itemCount())
                .as("清空购物车后仍有条目, url=%s", ctx.currentUrl())
                .isZero();
    }

    @When("买家从商品详情页加购测试商品 1 件")
    public void addTestGoodsFromDetail() {
        addOnceFromDetail();
        assertThat(ctx.currentUrl())
                .as("加购后未进入成功页, url=%s", ctx.currentUrl())
                .contains("/shoppingCart");
    }

    /**
     * 点一次加购，命中服务端限流（停在详情页）时等一个窗口再点第二次。
     */
    private void addOnceFromDetail() {
        for (int attempt = 0; attempt < 2; attempt++) {
            CartWriteRateLimiter.awaitSlot();
            new GoodsDetailPage(ctx.page())
                    .open(TestConfig.get().goodsId(), TestConfig.get().skuId())
                    .addToCart();
            if (ctx.currentUrl().contains("/shoppingCart")) {
                return;
            }
        }
    }

    @When("买家再次从商品详情页加购测试商品 1 件")
    public void addTestGoodsAgain() {
        addOnceFromDetail();
        assertThat(ctx.currentUrl())
                .as("二次加购后未进入成功页, url=%s", ctx.currentUrl())
                .contains("/shoppingCart");
    }

    @When("买家打开购物车页")
    public void openCartPage() {
        cart = new CartPage(ctx.page()).open();
        assertThat(ctx.currentUrl())
                .as("未进入购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("购物车角标应显示 1 件商品")
    public void badgeShowsOne() {
        // 角标只在上方导航渲染（购物车页自身是不带链接的纯文本），回首页读取
        String badge = new HomePage(ctx.page()).open().cartBadgeText();
        assertThat(badge)
                .as("购物车角标不是 1 件, 实际=%s, url=%s", badge, ctx.currentUrl())
                .contains("1");
    }

    @And("购物车应有 1 行商品")
    public void cartHasOneRow() {
        cart = new CartPage(ctx.page()).open();
        assertThat(cart.itemCount())
                .as("购物车行数不是 1, url=%s", ctx.currentUrl())
                .isEqualTo(1);
    }

    @And("该行商品数量应为 1")
    public void rowQuantityIsOne() {
        assertThat(cart.quantity())
                .as("购物车行数量不是 1, url=%s", ctx.currentUrl())
                .isEqualTo("1");
    }

    @And("该行商品数量应为 2")
    public void rowQuantityIsTwo() {
        cart = new CartPage(ctx.page()).open();
        assertThat(cart.quantity())
                .as("重复加购后数量不是 2, url=%s", ctx.currentUrl())
                .isEqualTo("2");
    }

    @And("购物车总价应为 3799 元")
    public void totalIs3799() {
        assertThat(cart.totalAmount())
                .as("购物车总价不符, url=%s", ctx.currentUrl())
                .isEqualTo(PRICE);
    }

    @And("购物车总价应为 7598 元")
    public void totalIs7598() {
        cart = new CartPage(ctx.page()).open();
        assertThat(cart.totalAmount())
                .as("重复加购后总价不符, url=%s", ctx.currentUrl())
                .isEqualTo(PRICE * 2);
    }

    @Given("买家购物车已通过造数放入测试商品 1 件")
    public void addViaApi() {
        CartFixture.seed(ctx.api(), CartFixture.mainGoods(1));
        cart = new CartPage(ctx.page()).open().awaitRowCount(1);
        assertThat(cart.itemCount())
                .as("接口加购后购物车不是 1 行, url=%s", ctx.currentUrl())
                .isEqualTo(1);
    }

    @Given("买家购物车已有测试商品 1 件")
    public void cartPreparedWithOneItem() {
        CartFixture.seed(ctx.api(), CartFixture.mainGoods(1));
        cart = new CartPage(ctx.page()).open().awaitRowCount(1);
        assertThat(cart.itemCount())
                .as("前置购物车不是 1 行, url=%s", ctx.currentUrl())
                .isEqualTo(1);
    }

    @When("买家把购物车数量改为该商品库存上限")
    public void setQuantityToStock() {
        stockLimit = new GoodsDetailPage(ctx.page()).open(TestConfig.get().goodsId()).stockCount();
        cart = new CartPage(ctx.page()).open();
        cart.setQuantity(String.valueOf(stockLimit));
        assertThat(cart.quantity())
                .as("库存上限=%d, 数量未回填, url=%s", stockLimit, ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("购物车数量应不超过库存上限")
    public void quantityNotExceedStock() {
        assertThat(Integer.parseInt(cart.quantity()))
                .as("购物车数量超过库存上限 %d, url=%s", stockLimit, ctx.currentUrl())
                .isLessThanOrEqualTo(stockLimit);
    }

    @When("买家点击购物车数量加号")
    public void clickPlus() {
        quantityBeforePlus = cart.quantity();
        cart.clickPlus();
        assertThat(quantityBeforePlus)
                .as("加号操作前未读到数量, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("购物车数量应仍不超过库存上限")
    public void quantityStillWithinStock() {
        assertThat(Integer.parseInt(cart.quantity()))
                .as("点加号后数量超过库存上限 %d, 之前=%s 现在=%s, url=%s",
                        stockLimit, quantityBeforePlus, cart.quantity(), ctx.currentUrl())
                .isLessThanOrEqualTo(stockLimit);
    }

    @And("购物车计费数量应不超过库存上限")
    public void billedQuantityWithinStock() {
        // 数量输入框允许输入大于可购上限的值，实际计费数量以总价/单价为准
        double billed = cart.totalAmount() / cart.itemPriceAmount();
        assertThat(billed)
                .as("计费数量超过库存上限 %d, 计费=%.2f 总价=%.2f, url=%s",
                        stockLimit, billed, cart.totalAmount(), ctx.currentUrl())
                .isLessThanOrEqualTo(stockLimit);
    }

    @When("买家把购物车数量改为 0")
    public void setCartQuantityZero() {
        cart.setQuantity("0");
        assertThat(ctx.currentUrl())
                .as("改数量后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("购物车数量应回退为 1")
    public void cartQuantityFallsBackToOne() {
        assertThat(cart.quantity())
                .as("非法数量未回退为 1, url=%s", ctx.currentUrl())
                .isEqualTo("1");
    }

    @When("买家把购物车数量改为 -1")
    public void setCartQuantityNegative() {
        cart.setQuantity("-1");
        assertThat(ctx.currentUrl())
                .as("改数量后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @When("买家把购物车数量改为 abc")
    public void setCartQuantityText() {
        cart.setQuantity("abc");
        assertThat(ctx.currentUrl())
                .as("改数量后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @When("买家把购物车数量改为 999999")
    public void setCartQuantityHuge() {
        cart.setQuantity("999999");
        assertThat(ctx.currentUrl())
                .as("改数量后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("购物车总价不应按 999999 计算")
    public void totalNotBasedOnHugeQuantity() {
        assertThat(cart.totalAmount())
                .as("总价按非法数量计算, 实际=%.2f, url=%s", cart.totalAmount(), ctx.currentUrl())
                .isEqualTo(cart.itemPriceAmount());
    }

    @When("买家把购物车数量改为 2")
    public void setCartQuantityTwo() {
        cart.setQuantity("2");
        assertThat(cart.quantity())
                .as("数量未改为 2, url=%s", ctx.currentUrl())
                .isEqualTo("2");
    }

    @When("买家点击购物车数量减号")
    public void clickMinus() {
        cart.clickMinus();
        assertThat(ctx.currentUrl())
                .as("减号操作后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("购物车数量应为 1")
    public void cartQuantityIsOne() {
        assertThat(cart.quantity())
                .as("减号后数量不是 1, url=%s", ctx.currentUrl())
                .isEqualTo("1");
    }

    @And("购物车数量减号应置灰")
    public void minusDisabled() {
        assertThat(cart.minusDisabled())
                .as("数量为 1 时减号未置灰, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @And("购物车小计应等于单价")
    public void subtotalEqualsPrice() {
        // 小计由服务端重算，先等它刷新到单价再取值，避免读到上一态
        cart.awaitSubtotalText(formatAmount(cart.itemPriceAmount()));
        assertThat(cart.itemSubtotalAmount())
                .as("小计与单价不符, url=%s", ctx.currentUrl())
                .isEqualTo(cart.itemPriceAmount());
    }

    private static String formatAmount(double amount) {
        return String.format("%,.2f", amount);
    }

    @Given("买家购物车已有测试商品与另一件低价商品")
    public void cartWithTwoItems() {
        CartFixture.seed(ctx.api(), CartFixture.mainGoods(1), CartFixture.secondStoreGoods(1));
        cart = new CartPage(ctx.page()).open().awaitRowCount(2);
        assertThat(cart.itemCount())
                .as("前置购物车不是 2 行, url=%s", ctx.currentUrl())
                .isEqualTo(2);
    }

    @Then("购物车总价应等于两件商品金额之和")
    public void totalEqualsSumOfTwo() {
        assertThat(cart.totalAmount())
                .as("两件商品总价不符, url=%s", ctx.currentUrl())
                .isEqualTo(PRICE + PRICE2);
    }

    @When("买家取消勾选第一行商品")
    public void uncheckFirstRow() {
        String before = cart.totalText();
        cart.toggleRow(0);
        cart.awaitTotalChangedFrom(before);
        assertThat(cart.selectedRowCount())
                .as("取消勾选后已选行数不是 1, url=%s", ctx.currentUrl())
                .isEqualTo(1);
    }

    @Then("购物车总价应等于剩余勾选商品金额")
    public void totalEqualsRemaining() {
        // 行顺序由购物车接口返回决定，不假设未勾选的是哪一行：总价必须等于其中一个单件金额
        assertThat(cart.totalAmount())
                .as("取消勾选后总价未重算, 总价=%.2f, url=%s", cart.totalAmount(), ctx.currentUrl())
                .isIn(PRICE, PRICE2);
    }

    @When("买家重新勾选第一行商品")
    public void recheckFirstRow() {
        String before = cart.totalText();
        cart.toggleRow(0);
        cart.awaitTotalChangedFrom(before);
        assertThat(cart.selectedRowCount())
                .as("重新勾选后已选行数不是 2, url=%s", ctx.currentUrl())
                .isEqualTo(2);
    }

    @Then("购物车总价应恢复为两件商品金额之和")
    public void totalRestored() {
        assertThat(cart.totalAmount())
                .as("重新勾选后总价未恢复, url=%s", ctx.currentUrl())
                .isEqualTo(PRICE + PRICE2);
    }

    @Given("买家购物车已有 3 件商品")
    public void cartWithThreeItems() {
        CartFixture.seed(ctx.api(), CartFixture.mainGoods(1), CartFixture.secondStoreGoods(1),
                CartFixture.thirdGoods(1));
        cart = new CartPage(ctx.page()).open().awaitRowCount(3);
        assertThat(cart.itemCount())
                .as("前置购物车不是 3 行, url=%s", ctx.currentUrl())
                .isEqualTo(3);
    }

    @When("买家删除购物车第一件商品")
    public void deleteFirstItem() {
        cart.deleteFirst();
        cart.awaitRowCount(2);
        assertThat(ctx.currentUrl())
                .as("删除后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("购物车应剩余 2 行商品")
    public void remainingTwoRows() {
        assertThat(cart.itemCount())
                .as("删除后行数不是 2, url=%s", ctx.currentUrl())
                .isEqualTo(2);
    }

    @When("买家点击清空购物车并取消确认")
    public void clearAllAndCancel() {
        cart.clearAllAndCancel();
        cart.awaitRowCount(2);
        assertThat(ctx.currentUrl())
                .as("清空操作后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("购物车应仍剩余 2 行商品")
    public void stillTwoRows() {
        assertThat(cart.itemCount())
                .as("取消清空后商品被删除, url=%s", ctx.currentUrl())
                .isEqualTo(2);
    }

    @Then("购物车页应显示已选择 1 件商品")
    public void selectedCountIsOne() {
        cart = new CartPage(ctx.page()).open();
        assertThat(cart.selectedCountText())
                .as("购物车「已选择」文案不符, 实际=%s, url=%s", cart.selectedCountText(), ctx.currentUrl())
                .contains("已选择1件商品");
    }

    @When("买家清除登录态后访问购物车页")
    public void openCartWithoutLogin() {
        Session.clear(ctx.context());
        cart = new CartPage(ctx.page()).open();
        assertThat(ctx.currentUrl())
                .as("游客访问购物车后离开了购物车路由, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("游客购物车应正常渲染且不展示账号商品")
    public void guestCartRenderedWithoutGoods() {
        assertThat(cart.itemCount())
                .as("游客购物车竟然展示了账号商品, url=%s", ctx.currentUrl())
                .isZero();
    }

    @When("买家重新注入登录态并刷新购物车")
    public void reloginAndReloadCart() {
        Session.inject(ctx.context(), ctx.api());
        cart = new CartPage(ctx.page()).reload();
        assertThat(ctx.currentUrl())
                .as("刷新后未停留在购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("购物车应仍展示该商品")
    public void cartStillHasGoods() {
        assertThat(cart.itemCount())
                .as("重新登录后购物车丢失商品, url=%s", ctx.currentUrl())
                .isEqualTo(1);
        assertThat(cart.firstItemText())
                .as("购物车商品名不符, url=%s", ctx.currentUrl())
                .contains(TestConfig.get().goodsName());
    }

    @Then("购物车应按店铺分组展示")
    public void cartGroupedByStore() {
        assertThat(cart.storeGroupCount())
                .as("购物车未按店铺分组, 分组数=%d, url=%s", cart.storeGroupCount(), ctx.currentUrl())
                .isGreaterThanOrEqualTo(2);
    }

    @And("各行商品名应包含各自商品")
    public void eachRowHasItsOwnGoods() {
        List<String> texts = cart.itemTexts();
        assertThat(texts)
                .as("购物车缺少主商品行, 实际=%s, url=%s", texts, ctx.currentUrl())
                .anyMatch(text -> text.contains(TestConfig.get().goodsName()));
        assertThat(texts)
                .as("购物车缺少第二店铺商品行, 实际=%s, url=%s", texts, ctx.currentUrl())
                .anyMatch(text -> text.contains("我是测试商品"));
    }

    @When("买家点击表头全选框")
    public void clickHeaderSelectAll() {
        cart.toggleSelectAllInHeader();
        assertThat(ctx.currentUrl())
                .as("全选操作后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @When("买家再次点击表头全选框")
    public void clickHeaderSelectAllAgain() {
        cart.toggleSelectAllInHeader();
        assertThat(ctx.currentUrl())
                .as("全选操作后离开了购物车页, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("已勾选商品数应为 0")
    public void checkedCountZero() {
        cart.awaitCheckedRows(0);
        assertThat(cart.selectedRowCount())
                .as("全不选后仍有勾选行, url=%s", ctx.currentUrl())
                .isZero();
    }

    @Then("已勾选商品数应为 3")
    public void checkedCountThree() {
        cart.awaitCheckedRows(3);
        assertThat(cart.selectedRowCount())
                .as("全选后勾选行数不是 3, url=%s", ctx.currentUrl())
                .isEqualTo(3);
    }

    @When("买家只勾选第一行商品")
    public void checkOnlyFirstRow() {
        // 前置可能处于全选态：先全部取消，再单选第一行
        if (cart.selectedRowCount() > 1) {
            cart.toggleSelectAllInHeader();
        }
        cart.toggleRow(0);
        assertThat(cart.selectedRowCount())
                .as("只勾选一行后已选行数不是 1, url=%s", ctx.currentUrl())
                .isEqualTo(1);
    }

    @Then("全选框不应处于已全选状态")
    public void selectAllNotFullyChecked() {
        // 当前构建 iView 不渲染半选样式（实测无 .ivu-checkbox-indeterminate），
        // 等价可观测信号：部分勾选时表头全选框不再显示为已勾选
        assertThat(cart.selectAllChecked())
                .as("部分勾选时表头全选框仍显示已全选, url=%s", ctx.currentUrl())
                .isFalse();
        assertThat(cart.selectedRowCount())
                .as("部分勾选时已选行数不是 1, url=%s", ctx.currentUrl())
                .isEqualTo(1);
    }

    @And("购物车总价应等于该行商品金额")
    public void totalEqualsThatRow() {
        // 勾选重算走异步，等总价从 0 刷新出来再取值
        cart.awaitTotalChangedFrom("0.00");
        assertThat(cart.totalAmount())
                .as("单选一行后总价不等于该行金额, 总价=%.2f, url=%s", cart.totalAmount(), ctx.currentUrl())
                .isIn(PRICE, PRICE2);
    }

    @When("买家直接打开购物车页")
    public void openCartAsGuest() {
        cart = new CartPage(ctx.page());
        boolean redirected = cart.openAndAwaitLoginRedirect();
        assertThat(redirected)
                .as("未登录访问购物车未跳登录, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @Then("应跳转到登录页并携带购物车回跳地址")
    public void loginRedirectWithReturnPath() {
        assertThat(ctx.currentUrl())
                .as("跳转地址不含登录页或回跳参数, url=%s", ctx.currentUrl())
                .contains("/login")
                .contains("rePath");
        assertThat(ctx.currentUrl())
                .as("回跳地址未指向购物车, url=%s", ctx.currentUrl())
                .containsIgnoringCase("cart");
    }
}
