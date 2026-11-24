package com.lili.ui.hooks;

import com.lili.ui.config.TestConfig;
import com.lili.ui.support.ApiClient;
import com.lili.ui.support.CartFixture;
import com.lili.ui.support.ScenarioContext;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import io.cucumber.java.After;
import io.cucumber.java.Before;

import java.net.URI;
import java.util.List;

/**
 * 登录态与数据前置。
 *
 * <p>登录策略（重要）：不使用 UI 登录，也不处理滑块。
 * 先调用登录接口拿 accessToken / refreshToken，再作为 cookie 注入浏览器上下文。
 * 实测 2026-11-12：注入后直接访问 /home/MyOrder 不再跳登录页。
 *
 * <p>清理策略：场景结束按登记的订单号逐个取消，避免待付款订单持续堆积。
 */
public class AuthHooks {

    private final ScenarioContext ctx;
    private final ApiClient api = new ApiClient();

    public AuthHooks(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Before(order = 1)
    public void loginAndPrepareData() {
        api.login();
        ctx.setApi(api);

        String host = URI.create(TestConfig.get().baseUrl()).getHost();
        BrowserContext context = ctx.context();
        context.addCookies(List.of(
                new Cookie("accessToken", api.accessToken()).setDomain(host).setPath("/"),
                new Cookie("refreshToken", api.refreshToken()).setDomain(host).setPath("/")
        ));

        // 清空购物车（直写 Redis，避开购物车写接口限流）
        CartFixture.clear(api);
    }

    @After(order = 50)
    public void cleanupCreatedOrders() {
        for (String orderSn : ctx.createdOrderSns()) {
            try {
                api.cancelOrder(orderSn, api.cancelReason());
            } catch (Exception ignored) {
                // 清理失败不改变用例结论，但会在日志里留痕
                System.err.println("[cleanup] 取消订单失败, sn=" + orderSn);
            }
        }
        ctx.createdOrderSns().clear();
    }
}
