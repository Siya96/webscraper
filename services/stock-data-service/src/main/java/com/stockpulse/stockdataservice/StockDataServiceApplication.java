package com.stockpulse.stockdataservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockDataServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(StockDataServiceApplication.class, args);
    }
}