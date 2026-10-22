package com.tradetrail;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.tradetrail")
@MapperScan("com.tradetrail.persistence")
public class TradeTrailApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeTrailApplication.class, args);
    }
}
