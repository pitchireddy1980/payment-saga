package com.paymentsaga.riskservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_assessments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false, unique = true)
    private String sagaId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Integer riskScore;

    @Column(nullable = false)
    private Boolean approved;

    @Column(nullable = false)
    private Boolean fraudCheck;

    @Column(nullable = false)
    private Boolean velocityCheck;

    @Column(nullable = false)
    private Boolean blacklistCheck;

    @Column(nullable = false)
    private Boolean rolledBack;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
