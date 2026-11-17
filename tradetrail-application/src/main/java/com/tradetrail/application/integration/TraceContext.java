package com.tradetrail.application.integration;

import java.util.UUID;

/**
 * 在当前请求范围传递追踪号，供状态审计使用。
 */
public final class TraceContext {
    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String current() {
        String value = TRACE.get();
        return value == null ? UUID.randomUUID().toString() : value;
    }

    public static void set(String value) {
        TRACE.set(value);
    }

    public static void clear() {
        TRACE.remove();
    }
}
