package com.crafting.blizz;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "blizzard")
public class BlizzConfig {
    private String clientId;
    private String clientSecret;
}
