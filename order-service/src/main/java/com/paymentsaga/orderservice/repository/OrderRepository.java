package com.paymentsaga.orderservice.repository;

import com.paymentsaga.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Order repository
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    
    Optional<Order> findBySagaId(String sagaId);
    
    Optional<Order> findByIdAndUserId(String id, String userId);
}
