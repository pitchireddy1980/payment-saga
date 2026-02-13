package com.paymentsaga.orderservice.dto;

import com.paymentsaga.orderservice.entity.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Create Order Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    @NotNull(message = "Payment method is required")
    private Order.PaymentMethod paymentMethod;

    @NotEmpty(message = "Items cannot be empty")
    @Valid
    private List<OrderItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        @NotBlank(message = "Product ID is required")
        private String productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        private BigDecimal price;
    }
}

/**
 * Order Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OrderResponse {
    private String orderId;
    private String userId;
    private String sagaId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private String transactionId;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
