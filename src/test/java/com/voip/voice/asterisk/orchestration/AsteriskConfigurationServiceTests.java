package com.voip.voice.asterisk.orchestration;

import com.voip.voice.asterisk.orchestration.services.AsteriskConfigurationService;
import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.ssh.sshmanagement.SFTPFileTransfer;
import com.voip.voice.ssh.sshmanagement.SSHExecuteCommand;
import net.schmizz.sshj.SSHClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.voip.voice.asterisk.orchestration.services.AsteriskConfigurationService.REMOTE_SETUP_SCRIPT_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for the remote configuration step (what gets uploaded and executed over SSH). */
class AsteriskConfigurationServiceTests {

    private SSHExecuteCommand sshExecuteCommand;
    private SFTPFileTransfer sftpFileTransfer;
    private AsteriskConfigurationService service;
    private final SSHClient sshClient = mock(SSHClient.class);
    private AsteriskServer server;

    @BeforeEach
    void setUp() {
        sshExecuteCommand = mock(SSHExecuteCommand.class);
        sftpFileTransfer = mock(SFTPFileTransfer.class);
        service = new AsteriskConfigurationService(sshExecuteCommand, sftpFileTransfer);

        server = new AsteriskServer();
        server.setIpAddress("10.10.1.101");
        server.setRealtimeDatabaseName("voice_asterisk");
        server.setRealtimeDatabaseUser("asterisk_voip");
        server.setRealtimeDatabasePassword("asterisk");
    }

    @DisplayName("Uploads the bundled setup script, makes it executable, then runs it with flags")
    @Test
    void configure_uploadsAndRunsScript() throws Exception {
        ArgumentCaptor<InputStream> uploaded = ArgumentCaptor.forClass(InputStream.class);
        doAnswer(inv -> {
            // read inside the answer: the stream is closed after upload returns
            String content = new String(inv.<InputStream>getArgument(1).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).startsWith("#!/usr/bin/env bash").doesNotContain("\r");
            return null;
        }).when(sftpFileTransfer).upload(eq(sshClient), uploaded.capture(), eq(REMOTE_SETUP_SCRIPT_PATH));

        assertThat(service.configureAsteriskServer(sshClient, server)).isTrue();

        ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(sftpFileTransfer, sshExecuteCommand);
        order.verify(sftpFileTransfer).upload(eq(sshClient), any(InputStream.class), eq(REMOTE_SETUP_SCRIPT_PATH));
        order.verify(sshExecuteCommand).execute(sshClient, "chmod +x " + REMOTE_SETUP_SCRIPT_PATH);
        order.verify(sshExecuteCommand).execute(eq(sshClient), command.capture());

        assertThat(command.getValue()).isEqualTo(
                "AST_DB_PASS='asterisk' " + REMOTE_SETUP_SCRIPT_PATH
                        + " --sip-ip '10.10.1.101' --db-name 'voice_asterisk' --db-user 'asterisk_voip' -y 2>&1");
    }

    @DisplayName("Values containing quotes are shell-escaped")
    @Test
    void configure_escapesShellValues() throws Exception {
        server.setRealtimeDatabasePassword("p'w; rm -rf /");

        service.configureAsteriskServer(sshClient, server);

        verify(sshExecuteCommand).execute(eq(sshClient), startsWith("AST_DB_PASS='p'\"'\"'w; rm -rf /' "));
    }

    @DisplayName("Upload failure returns false and the script is never run")
    @Test
    void configure_uploadFails() throws Exception {
        doThrow(new IOException("SFTP denied")).when(sftpFileTransfer).upload(any(), any(InputStream.class), anyString());

        assertThat(service.configureAsteriskServer(sshClient, server)).isFalse();
        verifyNoInteractions(sshExecuteCommand);
    }

    @DisplayName("Script exits non-zero: returns false")
    @Test
    void configure_scriptFails() throws Exception {
        when(sshExecuteCommand.execute(eq(sshClient), contains("--sip-ip")))
                .thenThrow(new IOException("Command execution failed"));

        assertThat(service.configureAsteriskServer(sshClient, server)).isFalse();
    }
}
