package com.example.smartspring.config;

import io.github.cdimascio.dotenv.Dotenv;

public class DotenvConfig {
    static {
        // Load .env file early in the application lifecycle
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
        
        // Set system properties from .env file
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });
    }
}
