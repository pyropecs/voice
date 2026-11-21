package com.voip.voice.asterisk.orchestration.services;

import com.voip.voice.asterisk.asteriskserver.repositories.AsteriskServerRepository;
import com.voip.voice.asterisk.asteriskserver.services.AsteriskServerService;
import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.enums.asterisk.AsteriskServerState;
import com.voip.voice.ssh.dto.SSHCredentiailsConfiguration;
import com.voip.voice.ssh.sshmanagement.SSHConnectionManagement;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
public class AsteriskOrchestrationService {
    private final AsteriskServerService asteriskServerService;
    private final SSHConnectionManagement sshConnectionManagement;
    private final AsteriskServerRepository asteriskServerRepository;
    private final AsteriskConfigurationService asteriskConfigurationService;
    public AsteriskOrchestrationService(AsteriskServerService asteriskServerService, SSHConnectionManagement sshConnectionManagement,AsteriskServerRepository asteriskServerRepository,AsteriskConfigurationService asteriskConfigurationService) {
        this.asteriskServerService = asteriskServerService;
        this.sshConnectionManagement = sshConnectionManagement;
        this.asteriskServerRepository = asteriskServerRepository;
        this.asteriskConfigurationService = asteriskConfigurationService;
    }

    private AsteriskServer getPendingAsteriskServer() {
        return asteriskServerService.getAsteriskServerByItsState(AsteriskServerState.PENDING);
    }

    public boolean setAsteriskServerInProcess(Integer id ){
        AsteriskServer asteriskInProcessServer = asteriskServerRepository.findById(id).orElse(null );
        if(asteriskInProcessServer != null) {
            asteriskInProcessServer.setAsteriskServerState(AsteriskServerState.CONFIGURING);
            asteriskServerRepository.save(asteriskInProcessServer);
            return true;
        }else{
            return false;
        }
    }

    public void processPendingAsteriskServer() {
        AsteriskServer asteriskServer = getPendingAsteriskServer();
        if (asteriskServer != null) {
            SSHCredentiailsConfiguration configurations = new SSHCredentiailsConfiguration(asteriskServer.getIpAddress(), asteriskServer.getSshUsername(), asteriskServer.getSshPassword());
            try (SSHClient client = sshConnectionManagement.connect(configurations)) {

                boolean updated = setAsteriskServerInProcess(asteriskServer.getId());
                if(updated) {
                    asteriskConfigurationService.configureAsteriskServer(client, asteriskServer);
                }else{
                    System.out.println("Failed to  Asterisk Server to configure");
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{
            System.out.println("No asterisk Pending server found");
        }
    }


}
