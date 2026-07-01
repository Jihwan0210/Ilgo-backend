package com.ilgo.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActionRepository extends JpaRepository<Action, UUID> {

    Optional<Action> findByIdAndAnalysisId(UUID id, UUID analysisId);
}
