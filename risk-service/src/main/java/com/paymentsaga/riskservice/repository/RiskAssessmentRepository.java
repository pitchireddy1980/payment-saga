package com.paymentsaga.riskservice.repository;

import com.paymentsaga.riskservice.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, String> {
    Optional<RiskAssessment> findBySagaId(String sagaId);
}
