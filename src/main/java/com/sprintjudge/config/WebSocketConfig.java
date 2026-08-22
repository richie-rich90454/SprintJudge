package com.sprintjudge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * Raises container buffer limits so full OJ submissions (up to 64KB of
     * source) fit in a single text message; the default 8KB buffer would
     * silently truncate legitimate code payloads.
     */
    @Bean
    public ServletServerContainerFactoryBean servletServerContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(256 * 1024);
        container.setMaxBinaryMessageBufferSize(256 * 1024);
        return container;
    }
}
