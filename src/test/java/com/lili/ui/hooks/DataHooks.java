package com.lili.ui.hooks;

import com.lili.ui.support.DbFixture;
import io.cucumber.java.After;

/**
 * 造数还原。
 *
 * <p>场景里用 {@link DbFixture#change} 改过的数据（库存、上下架）在结束后统一还原，
 * 保证「断言失败也不会把环境改坏」，后续场景仍从干净状态开始。
 */
public class DataHooks {

    @After(order = 90)
    public void restoreDatabase() {
        DbFixture.restoreAll();
    }
}
