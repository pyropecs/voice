package com.voip.voice.asterisk.servermanagement.services;

import com.voip.voice.asterisk.servermanagement.dtos.AsteriskServerDetails;
import com.voip.voice.asterisk.servermanagement.repositories.AsteriskServerRepository;
import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.enums.asterisk.AsteriskServerState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Service
public class AsteriskServerService {

    private final AsteriskServerRepository asteriskServerRepository;

    public AsteriskServerService(AsteriskServerRepository asteriskServerRepository) {
        this.asteriskServerRepository = asteriskServerRepository;
    }

    @Transactional
    public String createAsteriskServer(AsteriskServerDetails asteriskServerRequestDetails){
        if(!asteriskServerRepository.existsByIpAddress(asteriskServerRequestDetails.ipAddress())){
            AsteriskServer asteriskServer = new AsteriskServer();
            asteriskServer.setSshUsername(asteriskServerRequestDetails.sshUsername());
            asteriskServer.setSshPassword(asteriskServerRequestDetails.sshPassword());
            asteriskServer.setIpAddress(asteriskServerRequestDetails.ipAddress());
            String ASTERISK_DATABASE_NAME = "voice_asterisk";
            asteriskServer.setRealtimeDatabaseName(ASTERISK_DATABASE_NAME);
            String ASTERISK_DATABASE_PASSWORD = "asterisk";
            asteriskServer.setRealtimeDatabasePassword(ASTERISK_DATABASE_PASSWORD);
            String ASTERISK_DATABASE_USERNAME = "asterisk_voip";
            asteriskServer.setRealtimeDatabaseUser(ASTERISK_DATABASE_USERNAME);
            asteriskServer.setAsteriskServerState(AsteriskServerState.PENDING);
            asteriskServerRepository.save(asteriskServer);
        }else{
            return "Asterisk Server already exists";
        }

       return "Asterisk server created Successfully";

    }

    @Transactional
    public boolean updateAsteriskServer(int id, AsteriskServerDetails asteriskServerRequestDetails){
        AsteriskServer asteriskServer = asteriskServerRepository.findById(id).get();
        asteriskServer.setSshUsername(asteriskServerRequestDetails.sshUsername());
        asteriskServer.setSshPassword(asteriskServerRequestDetails.sshPassword());
        asteriskServer.setIpAddress(asteriskServerRequestDetails.ipAddress());
        asteriskServerRepository.save(asteriskServer);
        return true;
    }

    @Transactional
    public String deleteAsteriskServer(int id){
        Optional<AsteriskServer> asteriskServerResult = asteriskServerRepository.findById(id);

        if(asteriskServerResult.isPresent()){
            AsteriskServer asteriskServer = asteriskServerResult.get();
        asteriskServerRepository.delete(asteriskServer);
        return "Asterisk server deleted Successfully";
        }else{
            return "Asterisk Server Not Found";
        }
    }

    public AsteriskServerDetails getAsteriskServer(int id){
       Optional<AsteriskServer> asteriskServerResult = asteriskServerRepository.findById(id);
       if(asteriskServerResult.isPresent()){
           AsteriskServer asteriskServer = asteriskServerResult.get();
           return new AsteriskServerDetails(asteriskServer.getIpAddress(), asteriskServer.getSshUsername(), asteriskServer.getSshPassword());
       }else {
            return null;
       }

    }

    public List<AsteriskServerDetails> getAllAsteriskServers(){
        List<AsteriskServer> asteriskServers = asteriskServerRepository.findAll();
        return asteriskServers.stream()
                .map(asteriskServer -> new AsteriskServerDetails(asteriskServer.getIpAddress(),asteriskServer.getSshUsername(),asteriskServer.getSshPassword()) ).toList();
    }
    public AsteriskServerDetails getAsteriskServerByIPaddress(String ipAddress ){
        Optional<AsteriskServer> asteriskServerResult =asteriskServerRepository.findByIpAddress(ipAddress);
        if(asteriskServerResult.isPresent()){
            AsteriskServer asteriskServer = asteriskServerResult.get();
            return new AsteriskServerDetails(asteriskServer.getIpAddress(), asteriskServer.getSshUsername(), asteriskServer.getSshPassword());
        }else{
            return null;
        }



    }

    public AsteriskServer getAsteriskServerByItsState(AsteriskServerState asteriskServerState){
        Optional<AsteriskServer> asteriskServer = asteriskServerRepository.findFirstByAsteriskServerStateOrderByIdAsc(asteriskServerState);
        return asteriskServer.orElse(null);
    }

    /** All servers in the given state, oldest (lowest id) first. */
    public List<AsteriskServer> getAsteriskServersByItsState(AsteriskServerState asteriskServerState) {
        return asteriskServerRepository.findAllByAsteriskServerStateOrderByIdAsc(asteriskServerState);
    }



}
