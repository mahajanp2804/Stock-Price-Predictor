package com.capstone.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Web version of the app: a JSON API under /api and a browser UI served from
 * /static. Uses the same config (config.properties or environment variables),
 * database and migrations as the desktop app.
 */
@SpringBootApplication
@EnableScheduling
public class WebApp {
    public static void main(String[] args) {
        SpringApplication.run(WebApp.class, args);
    }
}
