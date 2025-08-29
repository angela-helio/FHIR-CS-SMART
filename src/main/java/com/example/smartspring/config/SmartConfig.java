package com.example.smartspring.config;

// SmartConfig.java
public record SmartConfig(
        String authorizationEndpoint,
        String tokenEndpoint,
        String userinfoEndpoint
) {}
