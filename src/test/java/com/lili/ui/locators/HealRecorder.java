package com.lili.ui.locators;

import java.util.ArrayList;
import java.util.List;

/**
 * 定位器自愈记录。
 *
 * <p>设计意图与 Python 版一致：走 fallback 不算失败，但**必须留痕**，
 * 否则定位器降级会被静默吃掉，验证者无法把「已降级」判成 PASS_WITH_WARN。
 *
 * <p>用 ThreadLocal 而非实例字段：Playwright 非线程安全，执行器按线程隔离，
 * 自愈记录也必须跟着线程走，否则并行时会串场景。
 */
public final class HealRecorder {

    private static final ThreadLocal<List<String>> HEALS = ThreadLocal.withInitial(ArrayList::new);

    private HealRecorder() {
    }

    public static void record(String message) {
        HEALS.get().add(message);
    }

    /** 取出并清空当前线程的自愈记录（由 hooks 在场景结束后调用）。 */
    public static List<String> consume() {
        List<String> copy = new ArrayList<>(HEALS.get());
        HEALS.get().clear();
        return copy;
    }
}
