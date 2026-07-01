package com.ilgo.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RiskRepository extends JpaRepository<Risk, UUID> {
}
