package se.rocketscien.mcp.springsshmcpserver.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistoryEntity, Long> {
}
