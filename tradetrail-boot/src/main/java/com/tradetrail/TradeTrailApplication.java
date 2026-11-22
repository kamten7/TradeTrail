package com.tradetrail;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商城启动入口只负责装配，交易规则由应用与领域层实现。
 */
@SpringBootApplication(scanBasePackages = "com.tradetrail")
@MapperScan("com.tradetrail.persistence")
public class TradeTrailApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeTrailApplication.class, args);
    }
}
