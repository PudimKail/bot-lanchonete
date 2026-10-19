package com.pudimkail.botlanchonete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BotLanchoneteApplication {

    public static void main(String[] args) {
        SpringApplication.run(BotLanchoneteApplication.class, args);
    }
}
