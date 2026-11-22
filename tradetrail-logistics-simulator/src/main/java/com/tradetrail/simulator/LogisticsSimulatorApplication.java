package com.tradetrail.simulator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 物流服务独立启动与迁移，不访问商城数据库。
 */
@SpringBootApplication
@MapperScan("com.tradetrail.simulator")
public class LogisticsSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogisticsSimulatorApplication.class, args);
    }
}
