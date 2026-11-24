package com.lili.ui.support;

import com.lili.ui.config.TestConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 购物车前置造数：直写 Redis 的 `{CART}_<memberId>` 快照。
 *
 * <p>为什么不用 HTTP 加购：`POST/DELETE /buyer/trade/carts` 在演示环境有较长窗口限流
 * （实测 6~10 秒间隔仍会 `code=1003`），多场景连跑必然互相踩，造数会变成不可控变量。
 * 购物车在 lilishop 里存 Redis，直接 SET 快照可稳定造 1~N 条数据。
 *
 * <p>快照模板来自一次真实加购后的 Redis 值（`fixtures/cart_snapshot.json`），
 * 结构含 Java 序列化标记（`@type`、`0L`），因此按文本模板处理而不是 JSON 解析。
 */
public final class CartFixture {

    private static final Pattern SKU_ID = Pattern.compile("\"id\":\"\\d+\"");
    private static final Pattern GOODS_ID = Pattern.compile("\"goodsId\":\"\\d+\"");
    private static final Pattern GOODS_NAME = Pattern.compile("\"goodsName\":\"[^\"]*\"");
    private static final Pattern PRICE = Pattern.compile("\"price\":[\\d.]+");
    private static final Pattern NUM = Pattern.compile("\"num\":\\d+");
    private static final Pattern STORE_ID = Pattern.compile("\"storeId\":\"\\d+\"");
    private static final Pattern STORE_NAME = Pattern.compile("\"storeName\":\"[^\"]*\"");
    private static final Pattern PURCHASE_PRICE = Pattern.compile("\"purchasePrice\":[\\d.]+");
    private static final Pattern SUB_TOTAL = Pattern.compile("\"subTotal\":[\\d.]+");
    private static final Pattern UTIL_PRICE = Pattern.compile("\"utilPrice\":[\\d.]+");

    private CartFixture() {
    }

    /** 一条购物车条目。 */
    public record Item(String skuId, String goodsId, String name, double price, String storeId, String storeName, int num) {
    }

    /** 主商品（自营店，多 SKU）。 */
    public static Item mainGoods(int num) {
        TestConfig config = TestConfig.get();
        return new Item(config.skuId(), config.goodsId(), config.goodsName().trim(),
                config.goodsPrice(), "1376369067769724928", "Lilishop自营店", num);
    }

    /** 第二店铺商品（家家乐，￥1.00）。 */
    public static Item secondStoreGoods(int num) {
        TestConfig config = TestConfig.get();
        return new Item(config.sku2Id(), config.goods2Id(), "我是测试商品",
                config.goods2Price(), "1376433565247471616", "家家乐", num);
    }

    /** 第三件商品（家家乐，￥1.00）。 */
    public static Item thirdGoods(int num) {
        TestConfig config = TestConfig.get();
        return new Item(config.sku3Id(), config.goods3Id(), "2323",
                config.goods2Price(), "1376433565247471616", "家家乐", num);
    }

    /** 清空购物车（删 key）。 */
    public static void clear(ApiClient api) {
        RedisClient.delete(keyOf(api));
    }

    /** 用给定条目整体替换购物车内容。 */
    public static void seed(ApiClient api, Item... items) {
        String template = template();
        String itemNode = extractFirstItem(template);
        List<String> nodes = new ArrayList<>();
        for (Item item : items) {
            nodes.add(render(itemNode, item));
        }
        String joined = String.join(",", nodes);
        String value = replaceList(template, "checkedSkuList", joined);
        value = replaceList(value, "skuList", joined);
        RedisClient.set(keyOf(api), value);
    }

    private static String keyOf(ApiClient api) {
        return "{CART}_" + api.memberId();
    }

    private static String template() {
        try (InputStream in = CartFixture.class.getResourceAsStream("/fixtures/cart_snapshot.json")) {
            if (in == null) {
                throw new IllegalStateException("缺少购物车快照模板 /fixtures/cart_snapshot.json");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            throw new IllegalStateException("读取购物车快照模板失败: " + e.getMessage(), e);
        }
    }

    /** 取 `checkedSkuList` 里的第一个条目节点（按花括号配对）。 */
    private static String extractFirstItem(String template) {
        int listStart = template.indexOf("\"checkedSkuList\":[");
        if (listStart < 0) {
            throw new IllegalStateException("模板缺少 checkedSkuList");
        }
        int nodeStart = template.indexOf('{', listStart);
        int depth = 0;
        for (int i = nodeStart; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return template.substring(nodeStart, i + 1);
                }
            }
        }
        throw new IllegalStateException("模板条目节点不完整");
    }

    private static String render(String node, Item item) {
        String result = SKU_ID.matcher(node).replaceFirst("\"id\":\"" + item.skuId() + "\"");
        result = GOODS_ID.matcher(result).replaceFirst("\"goodsId\":\"" + item.goodsId() + "\"");
        result = GOODS_NAME.matcher(result).replaceFirst("\"goodsName\":\"" + item.name() + "\"");
        result = PRICE.matcher(result).replaceFirst("\"price\":" + item.price());
        result = NUM.matcher(result).replaceFirst("\"num\":" + item.num());
        result = STORE_ID.matcher(result).replaceAll("\"storeId\":\"" + item.storeId() + "\"");
        result = STORE_NAME.matcher(result).replaceAll("\"storeName\":\"" + item.storeName() + "\"");
        result = PURCHASE_PRICE.matcher(result).replaceFirst("\"purchasePrice\":" + item.price());
        double subTotal = item.price() * item.num();
        result = SUB_TOTAL.matcher(result).replaceFirst("\"subTotal\":" + subTotal);
        return UTIL_PRICE.matcher(result).replaceFirst("\"utilPrice\":" + subTotal);
    }

    /** 把 `"<key>":[ ... ]` 的内容替换为 joined，保持外层结构不变。 */
    private static String replaceList(String template, String key, String joined) {
        String head = "\"" + key + "\":[";
        int start = template.indexOf(head);
        if (start < 0) {
            throw new IllegalStateException("模板缺少列表 " + key);
        }
        int contentStart = start + head.length();
        int depth = 1;
        for (int i = contentStart; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return template.substring(0, contentStart) + joined + template.substring(i);
                }
            }
        }
        throw new IllegalStateException("模板列表 " + key + " 不完整");
    }

    /** 模板里用到的 Pattern 在替换后可能丢失，保留以便排查。 */
    static boolean templateUsesSku(String template, String skuId) {
        Matcher matcher = SKU_ID.matcher(template);
        return matcher.find() && template.contains(skuId);
    }
}
