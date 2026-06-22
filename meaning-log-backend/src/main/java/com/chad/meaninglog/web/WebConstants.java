package com.chad.meaninglog.web;

public final class WebConstants {

    public static final String LOCAL_FRONTEND_ORIGIN = "http://localhost:5173";
    public static final String LOOPBACK_FRONTEND_ORIGIN = "http://127.0.0.1:5173";
    public static final String PRIMARY_VERCEL_ORIGIN = "https://meaning-log.vercel.app";
    public static final String FREEDNS_ORIGIN = "https://chada010.freeddns.org";
    public static final String FREEDNS_WWW_ORIGIN = "https://www.chada010.freeddns.org";
    public static final String VERCEL_ORIGIN_PATTERN = "https://*.vercel.app";

    public static final String SSE_BUFFERING_HEADER = "X-Accel-Buffering";
    public static final String CACHE_CONTROL_HEADER = "Cache-Control";
    public static final String NO_BUFFERING_VALUE = "no";
    public static final String NO_CACHE_VALUE = "no-cache";
    public static final long SSE_TIMEOUT_MS = 120_000L;
    public static final String SSE_DONE_EVENT = "done";
    public static final String SSE_SESSION_EVENT = "session";

    private WebConstants() {
    }
}
