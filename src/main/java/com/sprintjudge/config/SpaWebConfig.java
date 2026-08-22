package com.sprintjudge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA routing for the bundled frontend: unknown non-API GET paths fall back to
 * index.html so deep links like /admin/dashboard work straight from the jar.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{spring:[^.]*}").setViewName("forward:/index.html");
        registry.addViewController("/**/{spring:[^.]*}").setViewName("forward:/index.html");
    }
}
