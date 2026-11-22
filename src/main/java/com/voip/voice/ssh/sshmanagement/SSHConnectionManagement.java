package com.voip.voice.ssh.sshmanagement;



import com.voip.voice.ssh.dto.SSHCredentiailsConfiguration;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SSHConnectionManagement {

    static final int CONNECT_TIMEOUT_MS = 10_000;

    public SSHClient connect(SSHCredentiailsConfiguration details) throws IOException {
        SSHClient sshClient = new SSHClient();
        // Fail fast on unreachable hosts so orchestration can move on to the next server.
        sshClient.setConnectTimeout(CONNECT_TIMEOUT_MS);

        try {
            sshClient.loadKnownHosts();
            sshClient.connect(details.ipAddress());
            sshClient.authPassword(
                    details.sshUsername(),
                    details.sshPassword()
            );

            return sshClient;

        } catch (IOException exception) {
            try {
                sshClient.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }

            throw exception;
        }
    }








}
