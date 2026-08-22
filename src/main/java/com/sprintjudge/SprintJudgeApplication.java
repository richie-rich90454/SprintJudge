package com.openquiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OpenQuizApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenQuizApplication.class, args);
    }
}
