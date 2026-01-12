package com.example.readstory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class ReadStoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadStoryApplication.class, args);
    }

}
