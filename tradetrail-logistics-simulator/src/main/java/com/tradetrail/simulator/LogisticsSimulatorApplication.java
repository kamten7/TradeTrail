package com.tradetrail.simulator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tradetrail.simulator")
public class LogisticsSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogisticsSimulatorApplication.class, args);
    }
}
