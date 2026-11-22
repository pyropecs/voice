package com.voip.voice.asterisk.orchestration.services;


import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.ssh.sshmanagement.SFTPFileTransfer;
import com.voip.voice.ssh.sshmanagement.SSHExecuteCommand;
import net.schmizz.sshj.SSHClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class AsteriskConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(AsteriskConfigurationService.class);

    static final String SETUP_SCRIPT_RESOURCE = "scripts/asterisk-realtime-setup.sh";
    public static final String REMOTE_SETUP_SCRIPT_PATH = "/home/asterisk-realtime-setup.sh";

    private final SSHExecuteCommand sshExecuteCommand;
    private final SFTPFileTransfer sftpFileTransfer;

    public AsteriskConfigurationService(SSHExecuteCommand sshExecuteCommand, SFTPFileTransfer sftpFileTransfer) {
        this.sshExecuteCommand = sshExecuteCommand;
        this.sftpFileTransfer = sftpFileTransfer;
    }

    /**
     * Uploads the realtime setup script to the server and runs it.
     * @return true if the script finished with exit status 0.
     */
    public boolean configureAsteriskServer(SSHClient sshClient, AsteriskServer asteriskServer) {
        try {
            uploadSetupScript(sshClient);
            sshExecuteCommand.execute(sshClient, "chmod +x " + REMOTE_SETUP_SCRIPT_PATH);
            String result = sshExecuteCommand.execute(sshClient, buildSetupCommand(asteriskServer));
            log.info("Asterisk server {} configured. Script output:\n{}", asteriskServer.getIpAddress(), result);
            return true;
        } catch (Exception e) {
            log.error("Failed to configure Asterisk server {}: {}", asteriskServer.getIpAddress(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * The DB password is passed through AST_DB_PASS (supported by the script) so it does not show up in `ps`.
     * stderr is merged into stdout so the script cannot block on a full stderr buffer.
     */
    static String buildSetupCommand(AsteriskServer asteriskServer) {
        return "AST_DB_PASS=" + shellQuote(asteriskServer.getRealtimeDatabasePassword())
                + " " + REMOTE_SETUP_SCRIPT_PATH
                + " --sip-ip " + shellQuote(asteriskServer.getIpAddress())
                + " --db-name " + shellQuote(asteriskServer.getRealtimeDatabaseName())
                + " --db-user " + shellQuote(asteriskServer.getRealtimeDatabaseUser())
                + " -y 2>&1";
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void uploadSetupScript(SSHClient sshClient) throws IOException {
        ClassPathResource classPathResource = new ClassPathResource(SETUP_SCRIPT_RESOURCE);
        try (InputStream in = classPathResource.getInputStream()) {
            sftpFileTransfer.upload(sshClient, in, REMOTE_SETUP_SCRIPT_PATH);
        }
    }

}
