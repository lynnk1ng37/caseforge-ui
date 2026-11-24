package com.lili.ui.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MySQL 前置造数（库存、上下架、批发阶梯等买家端无法自造的数据）。
 *
 * <p>为什么走 `docker exec mysql`：环境里没有 JDBC 驱动也没有 mysql 客户端，
 * 但容器 `lilishop-all` 内自带 mysql 客户端，用它执行 SQL 最省依赖；
 * 造数只发生在测试前置，不在被测链路里。
 *
 * <p>还原约定：{@link #change(String, String)} 会登记反向 SQL，
 * {@code DataHooks} 在场景结束后统一执行，避免改动污染后续场景。
 */
public final class DbFixture {

    private static final List<String> RESTORE_SQL = new ArrayList<>();

    private DbFixture() {
    }

    /** 执行一条写 SQL，并登记还原 SQL（场景结束后执行）。 */
    public static void change(String sql, String restoreSql) {
        execute(sql);
        synchronized (RESTORE_SQL) {
            RESTORE_SQL.add(restoreSql);
        }
    }

    /** 直接执行（查询或不需要还原的写）。 */
    public static String execute(String sql) {
        List<String> command = List.of(
                "docker", "exec", container(),
                "mysql", "-uroot", "-p" + password(), "-N", database(), "-e", sql);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("SQL 执行超时: " + sql);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("SQL 执行失败: " + sql + "\n" + output);
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("无法执行 docker exec mysql: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SQL 执行被中断", e);
        }
    }

    /** 场景结束统一还原。 */
    public static void restoreAll() {
        List<String> pending;
        synchronized (RESTORE_SQL) {
            pending = new ArrayList<>(RESTORE_SQL);
            RESTORE_SQL.clear();
        }
        for (int i = pending.size() - 1; i >= 0; i--) {
            try {
                execute(pending.get(i));
            } catch (RuntimeException e) {
                System.err.println("[db-restore] 还原失败: " + pending.get(i) + " -> " + e.getMessage());
            }
        }
    }

    /** 改 SKU 库存并登记还原。 */
    public static void setSkuQuantity(String skuId, int quantity, int restoreQuantity) {
        change("update li_goods_sku set quantity=" + quantity + " where id=" + skuId + ";",
                "update li_goods_sku set quantity=" + restoreQuantity + " where id=" + skuId + ";");
    }

    /**
     * 商品整体下架：商品 + 其所有 SKU 一起置 DOWN。
     *
     * <p>只改 {@code li_goods} 不够：详情页可购买性还看 SKU 的 market_enable，
     * SKU 仍为 UPPER 时页面会照常渲染（实测）。
     */
    public static void setGoodsOffShelf(String goodsId) {
        change("update li_goods set market_enable='DOWN' where id=" + goodsId + ";",
                "update li_goods set market_enable='UPPER' where id=" + goodsId + ";");
        change("update li_goods_sku set market_enable='DOWN' where goods_id=" + goodsId + ";",
                "update li_goods_sku set market_enable='UPPER' where goods_id=" + goodsId + ";");
    }

    /**
     * 保证钱包余额不低于 target（余额支付会持续扣款，余额耗尽后收银台不再弹确认框）。
     * 场景结束后还原到执行前的余额。
     */
    public static void ensureWalletAtLeast(String memberId, double target) {
        String raw = execute("select member_wallet from li_member_wallet where member_id=" + memberId + ";").trim();
        double current = raw.isEmpty() ? 0d : Double.parseDouble(raw.split("\\s+")[0]);
        if (current >= target) {
            return;
        }
        change("update li_member_wallet set member_wallet=" + target + " where member_id=" + memberId + ";",
                "update li_member_wallet set member_wallet=" + current + " where member_id=" + memberId + ";");
    }

    /** 改商品上下架并登记还原。 */
    public static void setGoodsMarketEnable(String goodsId, String value, String restoreValue) {
        change("update li_goods set market_enable='" + value + "' where id=" + goodsId + ";",
                "update li_goods set market_enable='" + restoreValue + "' where id=" + goodsId + ";");
    }

    private static String container() {
        return env("UI_DB_CONTAINER", "lilishop-all");
    }

    private static String database() {
        return env("UI_DB_NAME", "lilishop");
    }

    private static String password() {
        return env("UI_DB_PASSWORD", "lilishop");
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
