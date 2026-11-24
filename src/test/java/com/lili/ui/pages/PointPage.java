package com.lili.ui.pages;

import com.lili.ui.locators.LocatorRegistry;
import com.lili.ui.locators.RouteRegistry;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** 我的积分页（/home/Point）。 */
public class PointPage {

    private static final Pattern REMAINING = Pattern.compile("剩余积分[：:]\\s*(\\d+)");
    private static final Pattern GAINED = Pattern.compile("累计获得[：:]\\s*(\\d+)");

    private final Page page;
    private final Locator summary;
    private final Locator gained;
    private final Locator logRows;

    public PointPage(Page page) {
        this.page = page;
        this.summary = LocatorRegistry.resolve(page, "asset.point_summary");
        this.gained = LocatorRegistry.resolve(page, "asset.point_gained");
        this.logRows = LocatorRegistry.resolve(page, "asset.point_log_first");
    }

    public PointPage open() {
        RouteRegistry.open(page, "my_point");
        assertThat(summary.first()).isVisible();
        return this;
    }

    public String summaryText() {
        return summary.first().innerText().trim();
    }

    public int remaining() {
        return number(REMAINING);
    }

    public int totalGained() {
        String text = gained.count() == 0 ? "" : gained.first().innerText().trim();
        Matcher matcher = GAINED.matcher(text.replaceAll("\\s+", " "));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    public String firstLogText() {
        return logRows.count() == 0 ? "" : logRows.first().innerText().trim();
    }

    private int number(Pattern pattern) {
        Matcher matcher = pattern.matcher(summaryText().replaceAll("\\s+", " "));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
