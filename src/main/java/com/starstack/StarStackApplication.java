package com.starstack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StarStackApplication {
    public static void main(String[] args) {
        SpringApplication.run(StarStackApplication.class, args);
    }
}