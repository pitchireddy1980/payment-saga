package com.paymentsaga.orderservice.controller;

import com.paymentsaga.orderservice.dto.CreateOrderRequest;
import com.paymentsaga.orderservice.dto.OrderResponse;
import com.paymentsaga.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Order REST Controller
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Service", description = "Order management and payment saga initiation")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/payment")
    @Operation(summary = "Initiate payment saga")
    public ResponseEntity<OrderResponse> initiatePayment(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId) {
        OrderResponse response = orderService.getOrder(orderId, userId);
        return ResponseEntity.ok(response);
    }
}
