package com.paperlineage.config;

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClientCustomizer bufferSizeCustomizer() {
        return builder -> builder.codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
    }
}
