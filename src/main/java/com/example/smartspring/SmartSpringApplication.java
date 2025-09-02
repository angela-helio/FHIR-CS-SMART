package com.example.smartspring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartSpringApplication {
    public static void main(String[] args) {
        // Force loading of DotenvConfig to ensure .env variables are loaded early
        try {
            Class.forName("com.example.smartspring.config.DotenvConfig");
        } catch (ClassNotFoundException e) {
            // Ignore - DotenvConfig is optional
        }
        
        SpringApplication.run(SmartSpringApplication.class, args);
    }
}
