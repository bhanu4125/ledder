package com.ledgerline.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    private static final Logger logger = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = isUuid(supplied) ? supplied : UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            logger.info("http_request method={} path={} status={} correlation_id={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), correlationId);
            MDC.remove("correlationId");
        }
    }

    private boolean isUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }
}
