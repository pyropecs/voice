package com.voip.voice.asterisk.asteriskserver.repositories;

import com.voip.voice.entities.AsteriskServer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsteriskServerRepository extends JpaRepository<AsteriskServer,Integer> {

    public AsteriskServer getByIpAddress(String ipAddress);


}
