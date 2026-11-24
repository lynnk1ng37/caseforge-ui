package com.lili.ui.support;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 极简 Redis 客户端（RESP 协议，仅 AUTH / SET / DEL / GET）。
 *
 * <p>为什么手写而不用 Jedis：只为测试前置造数服务，不值得为 4 个命令引入依赖；
 * 购物车在 lilishop 里存在 Redis（`{CART}_<memberId>`）而不是 MySQL，
 * 走 HTTP 加购会被服务端限流（code=1003），直写 Redis 可以稳定造多条购物车数据。
 *
 * <p>连接参数：`LILISHOP_REDIS_HOST`（默认 127.0.0.1）、`LILISHOP_REDIS_PORT`（6379）、
 * `LILISHOP_REDIS_PASSWORD`（默认 lilishop）。
 */
public final class RedisClient {

    private static final int TIMEOUT_MS = 5000;

    private RedisClient() {
    }

    public static void set(String key, String value) {
        String reply = command("SET", key, value);
        if (!reply.startsWith("+OK")) {
            throw new IllegalStateException("Redis SET 失败: " + reply);
        }
    }

    public static void delete(String key) {
        command("DEL", key);
    }

    public static String get(String key) {
        return command("GET", key);
    }

    /** 执行命令并返回响应首行（bulk string 时返回内容）。 */
    private static synchronized String command(String... args) {
        try (Socket socket = new Socket(host(), port())) {
            socket.setSoTimeout(TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            InputStream in = new BufferedInputStream(socket.getInputStream());

            auth(out, in);

            StringBuilder builder = new StringBuilder();
            builder.append("*").append(args.length).append("\r\n");
            for (String arg : args) {
                byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
                builder.append("$").append(bytes.length).append("\r\n");
                out.write(builder.toString().getBytes(StandardCharsets.UTF_8));
                out.write(bytes);
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                builder.setLength(0);
            }
            out.flush();
            return readReply(in);
        } catch (IOException e) {
            throw new IllegalStateException("Redis 命令失败(" + String.join(" ", args) + "): " + e.getMessage(), e);
        }
    }

    private static void auth(OutputStream out, InputStream in) throws IOException {
        String password = env("LILISHOP_REDIS_PASSWORD", "lilishop");
        if (password.isBlank()) {
            return;
        }
        String[] args = {"AUTH", password};
        StringBuilder builder = new StringBuilder("*2\r\n");
        for (String arg : args) {
            builder.append("$").append(arg.getBytes(StandardCharsets.UTF_8).length).append("\r\n").append(arg).append("\r\n");
        }
        out.write(builder.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
        readReply(in);
    }

    private static String readReply(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null) {
            throw new IllegalStateException("Redis 无响应");
        }
        if (line.startsWith("$-1") || line.startsWith("*-1")) {
            return "";
        }
        if (line.startsWith("$")) {
            return readBulk(in);
        }
        if (line.startsWith("*")) {
            return line;
        }
        return line;
    }

    private static String readBulk(InputStream in) throws IOException {
        String sizeLine = readLine(in);
        int size = Integer.parseInt(sizeLine == null ? "0" : sizeLine.trim());
        byte[] data = in.readNBytes(size);
        // 吃掉 bulk 结尾的 CRLF
        in.readNBytes(2);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int prev = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (prev == '\r' && current == '\n') {
                byte[] bytes = buffer.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }
            buffer.write(current);
            prev = current;
        }
        return null;
    }

    private static String host() {
        return env("LILISHOP_REDIS_HOST", "127.0.0.1");
    }

    private static int port() {
        return Integer.parseInt(env("LILISHOP_REDIS_PORT", "6379"));
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
