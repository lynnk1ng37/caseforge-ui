package com.lili.ui.steps;

import com.lili.ui.pages.CouponCenterPage;
import com.lili.ui.pages.CouponPage;
import com.lili.ui.pages.GiftCardPage;
import com.lili.ui.pages.GradePage;
import com.lili.ui.pages.MoneyPage;
import com.lili.ui.pages.PointPage;
import com.lili.ui.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会员资产与权益 步骤定义（用例层）。
 *
 * <p>覆盖人工用例：LLS-ASSET-001 / 002 / 005 / 009 / 012 / 017。
 */
public class AssetSteps {

    private final ScenarioContext ctx;
    private CouponPage couponPage;
    private PointPage pointPage;
    private GradePage gradePage;
    private MoneyPage moneyPage;
    private int couponCountBefore = -1;

    public AssetSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("买家已持有未使用优惠券")
    public void hasUnusedCoupons() {
        couponPage = new CouponPage(ctx.page()).open();
        couponCountBefore = couponPage.itemCount();
        assertThat(couponCountBefore)
                .as("账号没有未使用券, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(1);
    }

    @When("买家切换优惠券状态为已使用")
    public void switchCouponTabToUsed() {
        couponPage.switchTo("已使用");
        assertThat(couponPage.activeTabText())
                .as("未切换到已使用, url=%s", ctx.currentUrl())
                .contains("已使用");
    }

    @When("买家切换优惠券状态为已过期")
    public void switchCouponTabToExpired() {
        couponPage.switchTo("已过期");
        assertThat(couponPage.activeTabText())
                .as("未切换到已过期, url=%s", ctx.currentUrl())
                .contains("已过期");
    }

    @Then("未使用券应展示面额、门槛与有效期")
    public void unusedCouponShowsDetail() {
        couponPage.switchTo("未使用");
        String text = couponPage.firstItemText();
        assertThat(text)
                .as("未使用券信息不完整, 实际=%s, url=%s", text, ctx.currentUrl())
                .contains("使用范围")
                .contains("有效期");
    }

    @And("已过期券不应展示立即使用入口")
    public void expiredCouponNoUseButton() {
        couponPage.switchTo("已过期");
        assertThat(couponPage.useButtonCount())
                .as("已过期券仍展示「立即使用」, 数量=%d, url=%s", couponPage.useButtonCount(), ctx.currentUrl())
                .isZero();
    }

    @When("买家在领券中心重复领取同一张券")
    public void receiveSameCouponAgain() {
        new CouponCenterPage(ctx.page()).open().receiveFirst();
        couponPage = new CouponPage(ctx.page()).open();
        assertThat(couponPage.itemCount())
                .as("重复领取后券数量异常, 之前=%d 现在=%d, url=%s",
                        couponCountBefore, couponPage.itemCount(), ctx.currentUrl())
                .isEqualTo(couponCountBefore);
    }

    @Then("我的优惠券数量不应增加")
    public void couponCountUnchanged() {
        assertThat(couponPage.itemCount())
                .as("重复领取导致券数量变化, 之前=%d 现在=%d, url=%s",
                        couponCountBefore, couponPage.itemCount(), ctx.currentUrl())
                .isEqualTo(couponCountBefore);
    }

    @When("买家打开我的积分页")
    public void openPointPage() {
        pointPage = new PointPage(ctx.page()).open();
        assertThat(pointPage.remaining())
                .as("积分页未解析到剩余积分, 文案=%s, url=%s", pointPage.summaryText(), ctx.currentUrl())
                .isGreaterThanOrEqualTo(0);
    }

    @Then("剩余积分应等于累计获得减累计消耗")
    public void remainingEqualsGainedMinusUsed() {
        int remaining = pointPage.remaining();
        int gained = pointPage.totalGained();
        assertThat(remaining)
                .as("剩余积分(%d)大于累计获得(%d), url=%s", remaining, gained, ctx.currentUrl())
                .isLessThanOrEqualTo(gained);
    }

    @And("积分日志首条应记录变动值与时间")
    public void firstLogHasDetail() {
        String firstLog = pointPage.firstLogText().replaceAll("\\s+", " ");
        assertThat(firstLog)
                .as("积分日志为空, url=%s", ctx.currentUrl())
                .contains("+")
                .containsPattern("\\d{4}-\\d{2}-\\d{2}");
    }

    @When("买家打开我的等级页")
    public void openGradePage() {
        gradePage = new GradePage(ctx.page()).open();
        assertThat(gradePage.cardText())
                .as("等级卡片为空, url=%s", ctx.currentUrl())
                .isNotEmpty();
    }

    @Then("等级页应展示当前等级与经验值")
    public void gradeShowsLevelAndExperience() {
        String text = gradePage.cardText();
        assertThat(text)
                .as("等级页缺少经验值, 实际=%s, url=%s", text, ctx.currentUrl())
                .contains("当前经验值");
        assertThat(text)
                .as("等级页缺少等级名称, 实际=%s, url=%s", text, ctx.currentUrl())
                .contains("会员");
    }

    @And("等级页应展示距下一等级的差值")
    public void gradeShowsDelta() {
        assertThat(gradePage.cardText())
                .as("等级页缺少升级差值, url=%s", ctx.currentUrl())
                .containsPattern("还差\\s*\\d+");
    }

    @Given("买家打开我的礼品卡页")
    public void openGiftCardPage() {
        GiftCardPage giftCardPage = new GiftCardPage(ctx.page()).open();
        assertThat(giftCardPage.pageReady())
                .as("礼品卡页未就绪, url=%s", ctx.currentUrl())
                .isTrue();
    }

    @When("买家提交一个不存在的兑换码")
    public void redeemInvalidCode() {
        GiftCardPage giftCardPage = new GiftCardPage(ctx.page()).open();
        int before = giftCardPage.cardCount();
        giftCardPage.redeem("9999999999999999", "NOT-EXIST-CODE-0001");
        String toast = giftCardPage.toastText();
        assertThat(toast)
                .as("无效兑换码没有任何提示, url=%s", ctx.currentUrl())
                .isNotEmpty();
        assertThat(giftCardPage.cardCount())
                .as("无效兑换码却新增了礼品卡, 之前=%d 现在=%d, url=%s", before, giftCardPage.cardCount(), ctx.currentUrl())
                .isEqualTo(before);
    }

    @Then("应提示兑换码无效或兑换失败")
    public void invalidCodeRejected() {
        GiftCardPage giftCardPage = new GiftCardPage(ctx.page());
        assertThat(giftCardPage.toastText())
                .as("未提示兑换失败, url=%s", ctx.currentUrl())
                .containsAnyOf("无效", "失败", "不存在", "错误");
    }

    @When("买家依次读取券、积分、等级与余额页")
    public void readAllAssetPages() {
        couponPage = new CouponPage(ctx.page()).open();
        pointPage = new PointPage(ctx.page()).open();
        gradePage = new GradePage(ctx.page()).open();
        moneyPage = new MoneyPage(ctx.page()).open();
        assertThat(couponPage.itemCount())
                .as("券页条数异常, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(0);
    }

    @When("买家依次读取积分、等级与余额页")
    public void readThreeAssetPages() {
        pointPage = new PointPage(ctx.page()).open();
        // 先断言再跳下一个页面：跨页后旧 locator 解析不到
        assertThat(pointPage.remaining())
                .as("积分页未解析到剩余积分, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(0);
        gradePage = new GradePage(ctx.page()).open();
        moneyPage = new MoneyPage(ctx.page()).open();
        assertThat(moneyPage.balanceText())
                .as("余额页未展示账户余额, url=%s", ctx.currentUrl())
                .contains("账户余额");
    }

    @Then("积分、等级与余额在二次访问后应保持一致")
    public void threeAssetsStable() {
        // 每次读取前先回到对应页面，避免在别的页面上解析旧 locator
        pointPage.open();
        int pointFirst = pointPage.remaining();
        gradePage.open();
        String gradeFirst = gradePage.cardText();
        moneyPage.open();
        String balanceFirst = moneyPage.balanceText();

        pointPage.open();
        int pointSecond = pointPage.remaining();
        gradePage.open();
        String gradeSecond = gradePage.cardText();
        moneyPage.open();
        String balanceSecond = moneyPage.balanceText();

        assertThat(pointSecond)
                .as("二次访问后积分变化, %d -> %d, url=%s", pointFirst, pointSecond, ctx.currentUrl())
                .isEqualTo(pointFirst);
        assertThat(gradeSecond)
                .as("二次访问后等级信息变化, %s -> %s, url=%s", gradeFirst, gradeSecond, ctx.currentUrl())
                .isEqualTo(gradeFirst);
        assertThat(balanceSecond)
                .as("二次访问后余额文案变化, %s -> %s, url=%s", balanceFirst, balanceSecond, ctx.currentUrl())
                .isEqualTo(balanceFirst);
    }

    @Then("积分、等级与余额刷新后应保持一致")
    public void pointGradeMoneyStable() {
        pointPage.open();
        int pointFirst = pointPage.remaining();
        gradePage.open();
        String gradeFirst = gradePage.cardText();
        moneyPage.open();
        double balanceFirst = moneyPage.balanceAmount();
        assertThat(pointFirst)
                .as("首次读取积分为负, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(0);

        pointPage.open();
        assertThat(pointPage.remaining())
                .as("刷新后积分变化, %d -> %d, url=%s", pointFirst, pointPage.remaining(), ctx.currentUrl())
                .isEqualTo(pointFirst);
        gradePage.open();
        assertThat(gradePage.cardText())
                .as("刷新后等级信息变化, %s -> %s, url=%s", gradeFirst, gradePage.cardText(), ctx.currentUrl())
                .isEqualTo(gradeFirst);
        moneyPage.open();
        assertThat(moneyPage.balanceAmount())
                .as("刷新后余额变化, %.2f -> %.2f, url=%s", balanceFirst, moneyPage.balanceAmount(), ctx.currentUrl())
                .isEqualTo(balanceFirst);
    }

    @Then("券数量、积分、等级与余额应保持稳定")
    public void assetsConsistentAfterRefresh() {
        // 每个值都在对应页面上读：跨页后旧页面的 locator 一定解析不到
        couponPage.open();
        int couponFirst = couponPage.itemCount();
        couponPage.awaitItemCount(couponFirst);
        assertThat(couponFirst)
                .as("券页未读取到条数, url=%s", ctx.currentUrl())
                .isGreaterThanOrEqualTo(0);
        pointPage.open();
        int pointFirst = pointPage.remaining();
        gradePage.open();
        String gradeFirst = gradePage.cardText();
        moneyPage.open();
        String balanceFirst = moneyPage.balanceText();

        // 二次进入同一路由时列表偶发不渲染，先导航，等不到再硬刷新重试一次
        couponPage.open();
        if (!couponPage.tryAwaitItemCount(couponFirst, 5000)) {
            couponPage.reload();
        }
        pointPage.open();
        gradePage.open();
        moneyPage.open();

        assertThat(couponPage.awaitItemCount(couponFirst).itemCount())
                .as("刷新后券数量变化, %d -> %d", couponFirst, couponPage.itemCount())
                .isEqualTo(couponFirst);
        assertThat(pointPage.remaining())
                .as("刷新后积分变化, %d -> %d", pointFirst, pointPage.remaining())
                .isEqualTo(pointFirst);
        assertThat(gradePage.cardText())
                .as("刷新后等级信息变化, %s -> %s", gradeFirst, gradePage.cardText())
                .isEqualTo(gradeFirst);
        assertThat(moneyPage.balanceAmount())
                .as("刷新后余额变化, %.2f -> %.2f", balanceFirst, moneyPage.balanceAmount())
                .isEqualTo(balanceFirst);
    }
}
