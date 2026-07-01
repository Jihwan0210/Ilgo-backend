package com.ilgo.config;

import com.ilgo.common.CurrentUserArgumentResolver;
import com.ilgo.common.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor,
                     CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 인증은 Spring Security(JWT)가 담당. 여기서는 레이트리밋만.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/v1/analyses");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
