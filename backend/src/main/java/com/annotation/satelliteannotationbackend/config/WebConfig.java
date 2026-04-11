package com.annotation.satelliteannotationbackend.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * Web MVC 配置 - 静态文件服务
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String FRONTEND_DIST_PATH = "/Users/taolu/Downloads/test/satellite-annotation/frontend/dist/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射前端静态资源 - 支持 /gis/** 路径
        registry.addResourceHandler("/gis/**")
                .addResourceLocations("file:" + FRONTEND_DIST_PATH)
                .setCachePeriod(0);

        // 也支持根路径 /**
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + FRONTEND_DIST_PATH)
                .setCachePeriod(0);
    }

    /**
     * 缓存控制过滤器 - 强制禁用所有静态资源的浏览器缓存
     */
    @Configuration
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class NoCacheFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setHeader("Expires", "0");
            httpResponse.setHeader("X-Accel-Expires", "0");
            chain.doFilter(request, response);
        }
    }
}
