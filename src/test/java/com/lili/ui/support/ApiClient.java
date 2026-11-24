package com.lili.ui.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lili.ui.config.TestConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 界面测试所需的接口能力（登录、清空购物车、订单回查与清理）。
 *
 * <p>分层约定与 Python 版 {@code api/} 一致：只做 HTTP 调用与状态码校验，
 * **不做业务断言**，业务结果由 steps 层判定。
 *
 * <p>为什么必须走接口：lilishop 登录有滑块，UI 层登录无法稳定自动化。
 * 登录接口在本地环境已关闭服务端校验，携带任意 uuid 请求头即可。
 */
public class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl = TestConfig.get().apiBaseUrl();

    private String accessToken = "";
    private String refreshToken = "";

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    /**
     * 账号密码登录 {@code POST /buyer/passport/member/userLogin}。
     *
     * <p>form 表单 {@code username} + {@code password}（md5 小写 32 位），
     * {@code uuid} 与 {@code clientType} 放请求头（缺失会被网关直接 400）。
     */
    public void login() {
        String body = "username=" + encode(TestConfig.get().username())
                + "&password=" + encode(md5(TestConfig.get().password()));
        HttpRequest request = HttpRequest.newBuilder(uri("/buyer/passport/member/userLogin"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("clientType", "PC")
                .header("uuid", UUID.randomUUID().toString())
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode response = send(request, "账号密码登录");
        if (response.path("code").asInt() != 200) {
            throw new IllegalStateException("API 登录失败，无法构造 UI 登录态: " + response);
        }
        this.accessToken = response.path("result").path("accessToken").asText();
        this.refreshToken = response.path("result").path("refreshToken").asText();
    }

    /**
     * 从 accessToken（JWT）里解析买家 memberId。
     *
     * <p>登录响应只回 token，不回 id；JWT 的 userContext 声明里有 id，
     * 用它拼 Redis 的购物车 key（`{CART}_<memberId>`）。
     */
    public String memberId() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("尚未登录，无法解析 memberId");
        }
        String payload = accessToken.split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        try {
            JsonNode userContext = MAPPER.readTree(MAPPER.readTree(json).path("userContext").asText());
            String id = userContext.path("id").asText("");
            if (id.isBlank()) {
                throw new IllegalStateException("JWT 里没有 memberId: " + json);
            }
            return id;
        } catch (Exception e) {
            throw new IllegalStateException("解析 JWT 失败: " + e.getMessage(), e);
        }
    }

    /** 清空购物车 {@code DELETE /buyer/trade/carts}，保证条目数与金额断言确定。 */
    public void clearCart() {
        // 清空也是写操作，同样受服务端短窗口限流
        CartWriteRateLimiter.awaitSlot();
        JsonNode response = sendRetrying(() -> HttpRequest.newBuilder(uri("/buyer/trade/carts"))
                .header("clientType", "PC")
                .header("accessToken", accessToken)
                .timeout(Duration.ofSeconds(10))
                .DELETE()
                .build(), "清空购物车");
        if (response.path("code").asInt() != 200) {
            throw new IllegalStateException("清空购物车失败: " + response);
        }
    }

    /**
     * 购物车写操作重试：服务端 10 秒左右窗口内会返回 {@code code=1003}。
     *
     * <p>护栏只在调用前等一次，长场景里仍可能踩窗口，所以命中 1003 时再等一个窗口重试。
     */
    private JsonNode sendRetrying(java.util.function.Supplier<HttpRequest> factory, String action) {
        JsonNode response = send(factory.get(), action);
        for (int attempt = 0; attempt < 3 && response.path("code").asInt() == 1003; attempt++) {
            CartWriteRateLimiter.awaitSlot();
            response = send(factory.get(), action);
        }
        return response;
    }

    /**
     * 加购 {@code POST /buyer/trade/carts?skuId=&num=}，用于准备购物车前置数据。
     *
     * <p>注意不能把参数放 JSON body：实测 body 形式返回 code=400「服务器繁忙」。
     */
    public void addToCart(String skuId, int num) {
        // 加购接口有短窗口限流（code=1003），连续调用必须先过限流护栏
        CartWriteRateLimiter.awaitSlot();
        String path = "/buyer/trade/carts?skuId=" + encode(skuId) + "&num=" + num;
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("clientType", "PC")
                .header("accessToken", accessToken)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        JsonNode response = sendRetrying(() -> HttpRequest.newBuilder(uri(path))
                .header("clientType", "PC")
                .header("accessToken", accessToken)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), "加购");
        if (response.path("code").asInt() != 200) {
            throw new IllegalStateException("加购失败: " + response);
        }
    }

    /**
     * 分页查询订单 {@code GET /buyer/order/order}。
     *
     * @param tag {@code ALL} 全部 / {@code WAIT_PAY} 待付款
     */
    public List<JsonNode> pageOrders(String tag) {
        String path = "/buyer/order/order?pageNumber=1&pageSize=50&keywords=&tag=" + encode(tag);
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("clientType", "PC")
                .header("accessToken", accessToken)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        JsonNode response = send(request, "查询订单列表");
        List<JsonNode> records = new ArrayList<>();
        response.path("result").path("records").forEach(records::add);
        return records;
    }

    /** 取消订单 {@code POST /buyer/order/order/{orderSn}/cancel}，用于清理脏数据。 */
    public void cancelOrder(String orderSn, String reason) {
        String path = "/buyer/order/order/" + encode(orderSn) + "/cancel?orderSn=" + encode(orderSn)
                + "&reason=" + encode(reason);
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("clientType", "PC")
                .header("accessToken", accessToken)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        send(request, "取消订单");
    }

    /** 取取消原因字典的第一条，避免硬编码中文文案。 */
    public String cancelReason() {
        HttpRequest request = HttpRequest.newBuilder(
                        uri("/buyer/order/afterSale/get/afterSaleReason/CANCEL"))
                .header("clientType", "PC")
                .header("accessToken", accessToken)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        JsonNode response = send(request, "查询取消原因");
        JsonNode first = response.path("result").path(0);
        String reason = first.path("reason").asText("");
        return reason.isBlank() ? "测试数据清理" : reason;
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private JsonNode send(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            if (body.isBlank()) {
                return MAPPER.createObjectNode();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(action + " 失败, HTTP " + response.statusCode() + ", body: " + body);
            }
            return MAPPER.readTree(body);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(action + " 请求异常: " + e.getMessage(), e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hashed = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
