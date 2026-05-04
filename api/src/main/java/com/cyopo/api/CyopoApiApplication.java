package com.cyopo.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.cyopo")
@EnableAsync
@EnableScheduling
public class CyopoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CyopoApiApplication.class, args);
    }
}