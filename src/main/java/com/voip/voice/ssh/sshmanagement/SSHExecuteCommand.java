package com.voip.voice.ssh.sshmanagement;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SSHExecuteCommand {

    public String execute(SSHClient ssh, String command)
            throws IOException {

        try (Session session = ssh.startSession()) {

            Session.Command cmd = session.exec(command);

            String output = IOUtils.readFully(cmd.getInputStream())
                    .toString(StandardCharsets.UTF_8);

            cmd.join();

            if (cmd.getExitStatus() != 0) {
                throw new IOException(
                        "Command execution failed: " + command
                );
            }

            return output;
        }
    }

}
