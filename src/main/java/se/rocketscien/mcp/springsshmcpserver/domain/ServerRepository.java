package se.rocketscien.mcp.springsshmcpserver.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerRepository extends JpaRepository<ServerEntity, Long> {

    Optional<ServerEntity> findByName(String name);
}
