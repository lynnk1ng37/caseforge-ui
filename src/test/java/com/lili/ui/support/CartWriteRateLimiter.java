package com.lili.ui.support;

/**
 * 写操作限流护栏。
 *
 * <p>背景（实测 2026-11-12）：lilishop 对 {@code POST /buyer/trade/carts} 有短窗口限流，
 * 两条用例连续加购时第二次返回 {@code code=1003 访问过于频繁}，前端只弹一个 toast、
 * 不跳转，表现和「按钮没点中」完全一样，极易误判成定位问题。
 *
 * <p>为什么允许 sleep：分层门禁禁止固定等待，是为了避免用等待掩盖异步渲染。
 * 这里的等待针对的是**服务端限流窗口**，不是页面时序，属于基础设施约束，
 * 必须显式、可配置、可关闭，因此集中在本类而不是散落在 steps 里。
 *
 * <p>配置：{@code UI_CART_WRITE_INTERVAL} 两次写操作最小间隔秒数，默认 10；设 0 关闭。
 */
public final class CartWriteRateLimiter {

    private static final Object LOCK = new Object();
    private static long lastWriteAtNanos = 0L;

    private CartWriteRateLimiter() {
    }

    public static void awaitSlot() {
        double intervalSeconds = intervalSeconds();
        if (intervalSeconds <= 0) {
            return;
        }
        synchronized (LOCK) {
            long now = System.nanoTime();
            if (lastWriteAtNanos != 0L) {
                long waitNanos = (long) (intervalSeconds * 1_000_000_000L) - (now - lastWriteAtNanos);
                if (waitNanos > 0) {
                    try {
                        Thread.sleep(waitNanos / 1_000_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            lastWriteAtNanos = System.nanoTime();
        }
    }

    private static double intervalSeconds() {
        String raw = System.getenv("UI_CART_WRITE_INTERVAL");
        if (raw == null || raw.isBlank()) {
            return 10d;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 10d;
        }
    }
}
