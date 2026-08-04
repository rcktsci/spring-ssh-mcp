package ru.rcktsci.experiments.ai.sshmcp.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistoryEntity, Long> {
}
