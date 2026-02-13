package com.paymentsaga.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Order Service - Saga Initiator and Coordinator
 * 
 * Responsibilities:
 * - Initiate payment saga
 * - Manage order lifecycle
 * - Handle compensation (order cancellation)
 * - Coordinate with other services via events
 */
@SpringBootApplication
@EnableKafka
@EnableJpaAuditing
@EnableAsync
@ComponentScan(basePackages = {
    "com.paymentsaga.orderservice",
    "com.paymentsaga.common"
})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
