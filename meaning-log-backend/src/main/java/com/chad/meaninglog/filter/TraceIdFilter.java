package com.chad.meaninglog.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 每个请求进入时生成/继承 traceId 并放入 MDC，日志布局通过 %X{traceId} 输出。
 * Order 高于 JwtAuthenticationFilter（默认 Ordered.LOWEST_PRECEDENCE），确保鉴权日志也能带上 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveTraceId(String headerValue) {
        if (headerValue != null && !headerValue.isBlank()) {
            String trimmed = headerValue.trim();
            if (trimmed.length() <= 64) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
