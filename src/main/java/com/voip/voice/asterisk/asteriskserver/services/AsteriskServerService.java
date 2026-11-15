package com.voip.voice.asterisk.asteriskserver.services;

import com.voip.voice.asterisk.asteriskserver.dtos.AsteriskServerRequestDetails;
import com.voip.voice.asterisk.asteriskserver.repositories.AsteriskServerRepository;
import com.voip.voice.entities.AsteriskServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AsteriskServerService {

    private final AsteriskServerRepository asteriskServerRepository;

    public AsteriskServerService(AsteriskServerRepository asteriskServerRepository) {
        this.asteriskServerRepository = asteriskServerRepository;
    }

    @Transactional
    public boolean createAsteriskServer(AsteriskServerRequestDetails asteriskServerRequestDetails){

        AsteriskServer asteriskServer = new AsteriskServer();
        asteriskServer.setSshUsername(asteriskServerRequestDetails.sshUsername());
        asteriskServer.setSshPassword(asteriskServerRequestDetails.sshPassword());
        asteriskServer.setIpAddress(asteriskServerRequestDetails.ipAddress());
        String ASTERISK_DATABASE_NAME = "voice_asterisk";
        asteriskServer.setRealtimeDatabaseName(ASTERISK_DATABASE_NAME);
        String ASTERISK_DATABASE_PASSWORD = "asterisk";
        asteriskServer.setRealtimeDatabaseName(ASTERISK_DATABASE_PASSWORD);
        String ASTERISK_DATABASE_USERNAME = "asterisk_voip";
        asteriskServer.setRealtimeDatabaseName(ASTERISK_DATABASE_USERNAME);
       AsteriskServer asteriskServerResult = asteriskServerRepository.save(asteriskServer);
       return true;

    }

    @Transactional
    public boolean updateAsteriskServer(int id,AsteriskServerRequestDetails asteriskServerRequestDetails){
        AsteriskServer asteriskServer = asteriskServerRepository.findById(id).get();
        asteriskServer.setSshUsername(asteriskServerRequestDetails.sshUsername());
        asteriskServer.setSshPassword(asteriskServerRequestDetails.sshPassword());
        asteriskServer.setIpAddress(asteriskServerRequestDetails.ipAddress());
        asteriskServerRepository.save(asteriskServer);
        return true;
    }

    @Transactional
    public boolean deleteAsteriskServer(int id){
        AsteriskServer asteriskServer = asteriskServerRepository.findById(id).get();
        asteriskServerRepository.delete(asteriskServer);
        return true;
    }

    public AsteriskServer getAsteriskServer(int id){
        return asteriskServerRepository.findById(id).get();
    }

    public List<AsteriskServer> getAllAsteriskServers(){
        return asteriskServerRepository.findAll();
    }
    public AsteriskServer getAsteriskServerByIPaddress(String ipAddress ){
    return asteriskServerRepository.getByIpAddress(ipAddress);
    }


}
