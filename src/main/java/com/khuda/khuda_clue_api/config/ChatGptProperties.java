package com.khuda.khuda_clue_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chatgpt")
public record ChatGptProperties(Api api) {

    public record Api(String key) {
    }

    public String getApiKey() {
        return api != null ? api.key() : null;
    }
}
