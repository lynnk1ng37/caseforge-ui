package com.lili.ui.support;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个场景的共享状态。
 *
 * <p>Cucumber + PicoContainer 会按场景创建实例并注入到 steps / hooks 的构造函数，
 * 因此不需要静态字段，也不会跨场景串数据。
 */
public class ScenarioContext {

    private BrowserContext context;
    private Page page;
    private ApiClient api;
    private String tradeSn = "";
    private final List<String> createdOrderSns = new ArrayList<>();

    public BrowserContext context() {
        return context;
    }

    public void setContext(BrowserContext context) {
        this.context = context;
    }

    public Page page() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public ApiClient api() {
        return api;
    }

    public void setApi(ApiClient api) {
        this.api = api;
    }

    public String tradeSn() {
        return tradeSn;
    }

    public void setTradeSn(String tradeSn) {
        this.tradeSn = tradeSn == null ? "" : tradeSn;
    }

    public List<String> createdOrderSns() {
        return createdOrderSns;
    }

    /** 当前页面 URL，仅用于失败信息，不参与断言。 */
    public String currentUrl() {
        return page == null ? "<no page>" : page.url();
    }
}
