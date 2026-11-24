package com.lili.ui.support;

import com.lili.ui.config.TestConfig;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;

import java.net.URI;
import java.util.List;

/**
 * 登录态注入（场景内动态切换游客 / 登录态时使用）。
 *
 * <p>与 {@code AuthHooks} 的逻辑保持一致：lilishop 把 token 放 cookie，
 * 前端 axios 从存储读 token 塞请求头，所以 cookie 必须与页面同域。
 */
public final class Session {

    private Session() {
    }

    /** 把 API 会话的 accessToken / refreshToken 注入浏览器上下文。 */
    public static void inject(BrowserContext context, ApiClient api) {
        String host = URI.create(TestConfig.get().baseUrl()).getHost();
        context.addCookies(List.of(
                new Cookie("accessToken", api.accessToken()).setDomain(host).setPath("/"),
                new Cookie("refreshToken", api.refreshToken()).setDomain(host).setPath("/")
        ));
    }

    /** 清空浏览器 cookie，模拟未登录。 */
    public static void clear(BrowserContext context) {
        context.clearCookies();
    }
}
