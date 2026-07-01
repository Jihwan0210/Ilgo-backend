package com.ilgo.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 사용자당 분당 호출 제한(간단한 in-memory 카운터). POST /v1/analyses(AI 게이트웨이 호출) 에만 적용.
 * 분 단위 고정 윈도우로 카운트하며, 초과 시 429 RATE_LIMITED.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final int limitPerMin;
    private final Map<String, Window> counters = new ConcurrentHashMap<>();

    public RateLimitInterceptor(@Value("${app.rate-limit-per-min:20}") int limitPerMin) {
        this.limitPerMin = limitPerMin;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String key = currentUserKey();
        if (key == null) {
            return true; // 인증은 Security 필터 책임
        }
        long minute = System.currentTimeMillis() / 60_000L;
        Window window = counters.compute(key, (k, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Window(minute);
            }
            return existing;
        });
        int count = window.count.incrementAndGet();
        if (count > limitPerMin) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    ErrorCode.RATE_LIMITED.getDefaultMessage(),
                    Map.of("limit_per_min", limitPerMin));
        }
        return true;
    }

    private String currentUserKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            return auth.getPrincipal().toString();
        }
        return null;
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
