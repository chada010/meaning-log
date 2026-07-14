package com.chad.meaninglog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.chad.meaninglog.repository")
@SpringBootApplication
@EnableScheduling
public class MeaningLogBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeaningLogBackendApplication.class, args);
    }

}
