package com.lili.ui.steps;

import com.lili.ui.config.TestConfig;
import com.lili.ui.pages.CartPage;
import com.lili.ui.pages.CouponCenterPage;
import com.lili.ui.pages.GoodsDetailPage;
import com.lili.ui.pages.GoodsListPage;
import com.lili.ui.pages.HomePage;
import com.lili.ui.pages.OrderListPage;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首页与搜索 步骤定义（用例层）。
 *
 * <p>覆盖人工用例：LLS-HOME-001 / 002 / 004 / 005 / 006 / 010。
 *
 * <p>分层约束：只调用 page object，禁止裸定位、固定等待与硬编码 URL；业务断言集中在本层。
 */
public class HomeSteps {

    /** 无结果搜索用的中性关键字，保证 0 条结果且不携带敏感内容。 */
    private static final String NO_RESULT_KEYWORD = "zzz_no_such_goods_20261123";

    private final ScenarioContext ctx;
    private HomePage home;
    private GoodsListPage goodsList;
    private GoodsDetailPage detail;

    public HomeSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("买家已清除浏览器登录态")
    public void clearLoginState() {
        home = new HomePage(ctx.page()).openAsGuest();
        assertThat(home.categoryCount())
                .as("清 cookie 后首页未渲染分类，url=%s", ctx.currentUrl())
                .isGreaterThan(0);
    }

    @When("买家打开首页")
    public void openHome() {
        home = new HomePage(ctx.page()).open();
        assertThat(home.loginEntryVisible())
                .as("首页顶部登录入口未渲染, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @Then("首页分类导航应有至少 1 个分类")
    public void homeHasCategories() {
        assertThat(home.categoryCount())
                .as("首页分类导航为空, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(1);
    }

    @And("首页推荐商品应有至少 1 个商品")
    public void homeHasRecommendGoods() {
        assertThat(home.recommendCount())
                .as("首页推荐位为空, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(1);
    }

    @And("顶部应展示登录与注册入口")
    public void loginAndRegisterEntryVisible() {
        assertThat(home.loginEntryVisible())
                .as("顶部缺少「请登录」入口, url=%s", ctx.currentUrl())
                .isTrue();
        assertThat(home.registerEntryVisible())
                .as("顶部缺少「立即注册」入口, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @When("买家点击顶部购物车入口")
    public void clickCartEntry() {
        home = new HomePage(ctx.page()).open();
        ctx.setPage(home.clickCartEntry());
        assertThat(ctx.currentUrl())
                .as("点击购物车入口后未进入 /cart, url=%s", ctx.currentUrl())
                .contains("/cart");
    }

    @Then("购物车页应展示我的购物车步骤条")
    public void cartStepTitleVisible() {
        assertThat(new CartPage(ctx.page()).stepTitleText())
                .as("购物车页缺少「我的购物车」步骤条, url=%s", ctx.currentUrl())
                .contains("我的购物车");
    }

    @When("买家点击顶部我的订单入口")
    public void clickOrdersEntry() {
        home = new HomePage(ctx.page()).open();
        ctx.setPage(home.clickOrdersEntry());
        assertThat(ctx.currentUrl())
                .as("点击我的订单入口后未进入订单页, url=%s", ctx.currentUrl())
                .contains("/home/MyOrder");
    }

    @Then("我的订单页应展示订单页签")
    public void orderTabsVisible() {
        OrderListPage orderList = new OrderListPage(ctx.page()).expectLoaded();
        assertThat(orderList.tabCount())
                .as("订单页签数量不足 4 个, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(4);
    }

    @When("买家在搜索框输入一加并提交")
    public void searchOnePlus() {
        home = new HomePage(ctx.page()).open();
        boolean navigated = home.submitSearchAndAwaitResults("一加");
        assertThat(navigated)
                .as("提交搜索后未进入结果页, url=%s", ctx.currentUrl())
                .isTrue();
        goodsList = new GoodsListPage(ctx.page());
    }

    @Then("搜索结果应有至少 1 条商品")
    public void resultsNotEmpty() {
        assertThat(goodsList.itemCountAwait())
                .as("搜索结果为空, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(1);
    }

    @And("搜索结果首条商品名应包含一加")
    public void firstResultNameContainsKeyword() {
        assertThat(goodsList.firstName())
                .as("搜索结果首条名称不含关键字, url=%s", ctx.currentUrl())
                .contains("一加");
    }

    @When("买家点击搜索结果首条商品")
    public void openFirstResult() {
        ctx.setPage(goodsList.openFirstItem());
        detail = new GoodsDetailPage(ctx.page());
        assertThat(ctx.currentUrl())
                .as("点击首条结果后未进入商品详情, url=%s", ctx.currentUrl())
                .contains("/goodsDetail");
    }

    @Then("商品详情页标题应包含一加 OnePlus 9")
    public void detailTitleMatches() {
        assertThat(detail.titleText())
                .as("详情页标题不符, url=%s", ctx.currentUrl())
                .contains(TestConfig.get().goodsName());
    }

    @When("买家提交空关键字搜索")
    public void submitEmptyKeyword() {
        home = new HomePage(ctx.page()).open();
        home.submitSearchAndAwaitResults("");
        // 实测 2026-11-23：空关键字会跳到 /goodsList，但不携带 keyword 参数（按空值处理）
        assertThat(home.url())
                .as("空关键字页面已加载, url=%s", home.url())
                .isNotNull();
    }

    @Then("空关键字不应被作为检索词提交")
    public void emptyKeywordNotUsedAsQuery() {
        assertThat(ctx.currentUrl())
                .as("空关键字被当成检索词提交, url=%s", ctx.currentUrl())
                .doesNotContain("keyword=");
    }

    @When("买家在搜索框输入单字符 a 并提交")
    public void submitSingleChar() {
        home = new HomePage(ctx.page()).open();
        boolean navigated = home.submitSearchAndAwaitResults("a");
        assertThat(navigated)
                .as("单字符搜索未进入结果页, url=%s", ctx.currentUrl())
                .isTrue();
        goodsList = new GoodsListPage(ctx.page());
    }

    @Then("搜索结果页应展示关键字 a")
    public void resultPageShowsKeyword() {
        assertThat(goodsList.keywordText())
                .as("结果页未回显关键字, url=%s", ctx.currentUrl())
                .contains("a");
    }

    @When("买家在搜索框输入 60 个字符且不提交")
    public void fillSixtyChars() {
        home = new HomePage(ctx.page()).open();
        home.fillSearchOnly("字".repeat(60));
        assertThat(home.searchInputValue())
                .as("搜索框未接收输入, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("搜索框保留的字符数应不少于 50")
    public void searchBoxKeepsLongInput() {
        assertThat(home.searchInputValue().length())
                .as("搜索框输入被截断到 %d 字符, url=%s", home.searchInputValue().length(), ctx.currentUrl())
                .isGreaterThanOrEqualTo(50);
    }

    @When("买家搜索不存在的商品关键字")
    public void searchNoResultKeyword() {
        home = new HomePage(ctx.page()).open();
        boolean navigated = home.submitSearchAndAwaitResults(NO_RESULT_KEYWORD);
        assertThat(navigated)
                .as("无结果搜索未进入结果页, url=%s", ctx.currentUrl())
                .isTrue();
        goodsList = new GoodsListPage(ctx.page());
    }

    @Then("搜索结果应为 0 条商品")
    public void resultsAreEmpty() {
        assertThat(goodsList.itemCountAwait())
                .as("无结果关键字仍返回商品, url=%s", ctx.currentUrl())
                .isZero();
    }

    @When("买家打开首页并读取顶部入口")
    public void openHomeAndReadEntries() {
        home = new HomePage(ctx.page()).open();
        assertThat(home.navOrdersText())
                .as("顶部我的订单入口为空, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("顶部应可读到我的订单入口")
    public void ordersEntryReadable() {
        assertThat(home.navOrdersText())
                .as("顶部未读到「我的订单」入口, url=%s", ctx.currentUrl())
                .contains("我的订单");
    }

    @And("顶部应可读到秒杀与优惠券入口")
    public void activityEntriesReadable() {
        assertThat(home.navSeckillText())
                .as("顶部未读到「秒杀」入口, url=%s", ctx.currentUrl())
                .contains("秒杀");
        assertThat(home.navCouponText())
                .as("顶部未读到「优惠券」入口, url=%s", ctx.currentUrl())
                .contains("优惠券");
    }

    @When("买家点击顶部优惠券入口")
    public void clickCouponEntry() {
        home = new HomePage(ctx.page()).open();
        ctx.setPage(home.clickCouponEntry());
        assertThat(ctx.currentUrl())
                .as("点击优惠券入口后未进入 /coupon, url=%s", ctx.currentUrl())
                .contains("/coupon");
    }

    @Then("领券中心页应展示领券中心标题")
    public void couponCenterVisible() {
        assertThat(new CouponCenterPage(ctx.page()).pageFlagVisible())
                .as("领券中心页未展示标题, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @When("买家点击顶部秒杀入口")
    public void clickSeckillEntry() {
        home = new HomePage(ctx.page()).open();
        ctx.setPage(home.clickSeckillEntry());
        assertThat(ctx.currentUrl())
                .as("点击秒杀入口后未进入 /seckill, url=%s", ctx.currentUrl())
                .contains("/seckill");
    }

    @Then("秒杀页应展示限时秒杀")
    public void seckillPageVisible() {
        assertThat(home.seckillPageVisible())
                .as("秒杀页未展示限时秒杀文案, url=%s", ctx.currentUrl())
                .isTrue();
    }
}
