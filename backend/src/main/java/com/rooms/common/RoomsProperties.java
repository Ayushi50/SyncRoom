package com.rooms.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rooms")
public record RoomsProperties(
    String jwtSecret,
    String frontendOrigin,
    String appBaseUrl,
    String mailFrom,
    boolean seed,
    boolean cookieSecure) {}
