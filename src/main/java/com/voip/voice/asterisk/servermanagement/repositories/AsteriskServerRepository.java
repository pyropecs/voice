package com.voip.voice.asterisk.servermanagement.repositories;

import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.enums.asterisk.AsteriskServerState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AsteriskServerRepository extends JpaRepository<AsteriskServer,Integer> {

    public AsteriskServer getByIpAddress(String ipAddress);
    public boolean existsByIpAddress(String ipAddress);
    public Optional<AsteriskServer> findByIpAddress(String ipAddress);

    public Optional<AsteriskServer> findFirstByAsteriskServerStateOrderByIdAsc(AsteriskServerState asteriskServerState);

    public List<AsteriskServer> findAllByAsteriskServerStateOrderByIdAsc(AsteriskServerState asteriskServerState);
}
