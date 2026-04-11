package com.annotation.satelliteannotationbackend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 前端路由控制器 - 处理 SPA 路由
 */
@Controller
public class FrontendController {

    /**
     * 根路径重定向到 index.html
     */
    @GetMapping("/")
    public RedirectView redirectToIndex() {
        return new RedirectView("/index.html");
    }

    /**
     * /gis/ 路径重定向到 /gis/index.html
     */
    @GetMapping("/gis/")
    public RedirectView GisToIndex() {
        return new RedirectView("/gis/index.html");
    }

    /**
     * 处理前端路由 - 所有未知的 GET 请求都返回 index.html
     */
    @GetMapping("/{path:[^api]*}")
    public RedirectView forwardToIndex() {
        return new RedirectView("/index.html");
    }
}
