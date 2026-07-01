package com.ilgo.config;

import com.ilgo.common.CurrentUserArgumentResolver;
import com.ilgo.common.DeviceIdInterceptor;
import com.ilgo.common.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final DeviceIdInterceptor deviceIdInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(DeviceIdInterceptor deviceIdInterceptor,
                     RateLimitInterceptor rateLimitInterceptor,
                     CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.deviceIdInterceptor = deviceIdInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1) 디바이스 식별/검증 → 2) 레이트리밋. /v1/** 전체에 device 검증 적용.
        registry.addInterceptor(deviceIdInterceptor)
                .addPathPatterns("/v1/**")
                .order(0);
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/v1/analyses")
                .order(1);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
