package com.sprintjudge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SprintJudgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SprintJudgeApplication.class, args);
    }
}
