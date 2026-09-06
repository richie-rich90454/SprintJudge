package com.sprintjudge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // The shell references hashed assets, but index.html itself must never
        // be cached: otherwise browsers keep booting the previous deployment's
        // JS after an upgrade and report already-fixed bugs as still present.
        // (Location is the static ROOT: pointing at the file itself makes
        // Spring treat it as a directory and every forward 404s.)
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore());
    }
}
