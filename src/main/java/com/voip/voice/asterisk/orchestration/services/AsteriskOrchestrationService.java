package com.voip.voice.asterisk.orchestration.services;

import com.voip.voice.asterisk.servermanagement.repositories.AsteriskServerRepository;
import com.voip.voice.asterisk.servermanagement.services.AsteriskServerService;
import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.enums.asterisk.AsteriskServerState;
import com.voip.voice.ssh.dto.SSHCredentiailsConfiguration;
import com.voip.voice.ssh.sshmanagement.SSHConnectionManagement;
import net.schmizz.sshj.SSHClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class AsteriskOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AsteriskOrchestrationService.class);

    private final AsteriskServerService asteriskServerService;
    private final SSHConnectionManagement sshConnectionManagement;
    private final AsteriskServerRepository asteriskServerRepository;
    private final AsteriskConfigurationService asteriskConfigurationService;

    public AsteriskOrchestrationService(AsteriskServerService asteriskServerService, SSHConnectionManagement sshConnectionManagement, AsteriskServerRepository asteriskServerRepository, AsteriskConfigurationService asteriskConfigurationService) {
        this.asteriskServerService = asteriskServerService;
        this.sshConnectionManagement = sshConnectionManagement;
        this.asteriskServerRepository = asteriskServerRepository;
        this.asteriskConfigurationService = asteriskConfigurationService;
    }

    private List<AsteriskServer> getPendingAsteriskServers() {
        return asteriskServerService.getAsteriskServersByItsState(AsteriskServerState.PENDING);
    }

    public boolean setAsteriskServerInProcess(Integer id) {
        AsteriskServer asteriskInProcessServer = asteriskServerRepository.findById(id).orElse(null);
        if (asteriskInProcessServer != null) {
            asteriskInProcessServer.setAsteriskServerState(AsteriskServerState.CONFIGURING);
            asteriskServerRepository.save(asteriskInProcessServer);
            return true;
        } else {
            return false;
        }
    }

    private void setAsteriskServerConfigured(Integer id) {
        asteriskServerRepository.findById(id).ifPresent(server -> {
            server.setAsteriskServerState(AsteriskServerState.CONFIGURED);
            asteriskServerRepository.save(server);
        });
    }

    /**
     * Reachability test: opens an SSH session (TCP connect + host key check + login).
     * @return the connected client, or empty if the server cannot be reached / logged into.
     */
    private Optional<SSHClient> connectIfReachable(AsteriskServer asteriskServer) {
        SSHCredentiailsConfiguration configurations = new SSHCredentiailsConfiguration(
                asteriskServer.getIpAddress(), asteriskServer.getSshUsername(), asteriskServer.getSshPassword());
        try {
            return Optional.of(sshConnectionManagement.connect(configurations));
        } catch (IOException e) {
            log.warn("Asterisk server {} (id {}) is not reachable over SSH, skipping: {}",
                    asteriskServer.getIpAddress(), asteriskServer.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Walks the PENDING servers oldest first. Servers that are not reachable over SSH are skipped
     * (they stay PENDING and are retried on the next run). The first reachable server is configured.
     * At most one server is configured per run.
     *
     * @return the server that was picked for configuration, or empty if none was reachable.
     */
    public Optional<AsteriskServer> processPendingAsteriskServer() {
        List<AsteriskServer> pendingServers = getPendingAsteriskServers();
        if (pendingServers.isEmpty()) {
            log.debug("No asterisk pending server found");
            return Optional.empty();
        }

        for (AsteriskServer asteriskServer : pendingServers) {
            Optional<SSHClient> connection = connectIfReachable(asteriskServer);
            if (connection.isEmpty()) {
                continue; // move on to the next pending server
            }

            try (SSHClient client = connection.get()) {
                if (!setAsteriskServerInProcess(asteriskServer.getId())) {
                    log.warn("Asterisk server id {} disappeared before configuration", asteriskServer.getId());
                    continue;
                }
                boolean configured = asteriskConfigurationService.configureAsteriskServer(client, asteriskServer);
                if (configured) {
                    setAsteriskServerConfigured(asteriskServer.getId());
                }
                // On failure the server stays CONFIGURING so it is not retried in a loop.
                return Optional.of(asteriskServer);
            } catch (IOException e) {
                // Only thrown by closing the SSH client; configuration result is already recorded.
                log.warn("Failed to close SSH connection to {}: {}", asteriskServer.getIpAddress(), e.getMessage());
                return Optional.of(asteriskServer);
            }
        }

        log.warn("None of the {} pending asterisk servers are reachable over SSH", pendingServers.size());
        return Optional.empty();
    }
}
