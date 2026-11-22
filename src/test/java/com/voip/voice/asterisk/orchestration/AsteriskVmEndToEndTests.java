package com.voip.voice.asterisk.orchestration;

import com.voip.voice.VoiceApplication;
import com.voip.voice.asterisk.orchestration.services.AsteriskOrchestrationService;
import com.voip.voice.asterisk.servermanagement.repositories.AsteriskServerRepository;
import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.enums.asterisk.AsteriskServerState;
import com.voip.voice.ssh.dto.SSHCredentiailsConfiguration;
import com.voip.voice.ssh.sshmanagement.SSHConnectionManagement;
import com.voip.voice.ssh.sshmanagement.SSHExecuteCommand;
import net.schmizz.sshj.SSHClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests against a REAL Asterisk VM. Nothing is mocked.
 *
 * WARNING: the full-flow test runs asterisk-realtime-setup.sh on the VM as root
 * (installs MariaDB/unixODBC, creates the realtime DB, rewrites Asterisk config).
 *
 * Skipped unless ASTERISK_VM_SSH_PASSWORD is set. Optional overrides:
 *   ASTERISK_VM_IP           (default 10.10.1.101)
 *   ASTERISK_VM_SSH_USERNAME (default root)
 *
 * The VM's host key must be in ~/.ssh/known_hosts (SSHConnectionManagement verifies it).
 * DB rows created here are rolled back; changes on the VM are not.
 */
@SpringBootTest(classes = VoiceApplication.class)
@Transactional
@Tag("vm")
@EnabledIfEnvironmentVariable(named = "ASTERISK_VM_SSH_PASSWORD", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AsteriskVmEndToEndTests {

    private static final String VM_IP = envOrDefault("ASTERISK_VM_IP", "10.10.1.101");
    private static final String VM_USER = envOrDefault("ASTERISK_VM_SSH_USERNAME", "root");
    private static final String VM_PASSWORD = System.getenv("ASTERISK_VM_SSH_PASSWORD");

    @Autowired
    private AsteriskOrchestrationService asteriskOrchestrationService;

    @Autowired
    private AsteriskServerRepository asteriskServerRepository;

    @Autowired
    private SSHConnectionManagement sshConnectionManagement;

    @Autowired
    private SSHExecuteCommand sshExecuteCommand;

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private SSHClient connect() throws Exception {
        return sshConnectionManagement.connect(new SSHCredentiailsConfiguration(VM_IP, VM_USER, VM_PASSWORD));
    }

    @Order(1)
    @DisplayName("VM: SSH login and command execution work")
    @Test
    void sshConnectionToVm() throws Exception {
        try (SSHClient client = connect()) {
            assertThat(client.isAuthenticated()).isTrue();
            assertThat(sshExecuteCommand.execute(client, "echo ok").trim()).isEqualTo("ok");
            assertThat(sshExecuteCommand.execute(client, "whoami").trim()).isEqualTo(VM_USER);
        }
    }

    /** An address in TEST-NET-1 (RFC 5737): guaranteed not to answer, so the SSH connect times out. */
    private static final String UNREACHABLE_IP = "192.0.2.1";

    private AsteriskServer savePending(String ip, String user, String password) {
        AsteriskServer server = new AsteriskServer();
        server.setIpAddress(ip);
        server.setSshUsername(user);
        server.setSshPassword(password);
        server.setRealtimeDatabaseName("voice_asterisk");
        server.setRealtimeDatabaseUser("asterisk_voip");
        server.setRealtimeDatabasePassword("asterisk");
        server.setAsteriskServerState(AsteriskServerState.PENDING);
        return asteriskServerRepository.saveAndFlush(server);
    }

    @Order(2)
    @DisplayName("VM: unreachable server ahead in the queue is skipped, VM is configured and ends CONFIGURED")
    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void pendingServerIsConfiguredOnVm() throws Exception {
        // Make sure only our servers are PENDING (all changes rolled back after the test).
        asteriskServerRepository.findByIpAddress(VM_IP).ifPresent(asteriskServerRepository::delete);
        asteriskServerRepository.findByIpAddress(UNREACHABLE_IP).ifPresent(asteriskServerRepository::delete);
        asteriskServerRepository.findAll().stream()
                .filter(s -> s.getAsteriskServerState() == AsteriskServerState.PENDING)
                .forEach(s -> s.setAsteriskServerState(AsteriskServerState.CONFIGURED));
        asteriskServerRepository.flush();

        // Older (lower id) unreachable server is first in the queue.
        AsteriskServer unreachable = savePending(UNREACHABLE_IP, "root", "irrelevant");
        AsteriskServer server = savePending(VM_IP, VM_USER, VM_PASSWORD);

        assertThat(asteriskOrchestrationService.processPendingAsteriskServer())
                .get().extracting(AsteriskServer::getIpAddress).isEqualTo(VM_IP);

        assertThat(asteriskServerRepository.findById(unreachable.getId()).orElseThrow().getAsteriskServerState())
                .isEqualTo(AsteriskServerState.PENDING);

        AsteriskServer result = asteriskServerRepository.findById(server.getId()).orElseThrow();
        assertThat(result.getAsteriskServerState())
                .as("setup script should succeed - check the test log for the script output")
                .isEqualTo(AsteriskServerState.CONFIGURED);

        try (SSHClient client = connect()) {
            assertThat(sshExecuteCommand.execute(client, "test -x /home/asterisk-realtime-setup.sh && echo present").trim())
                    .isEqualTo("present");
            assertThat(sshExecuteCommand.execute(client, "cat /etc/odbc.ini")).contains("[asterisk]");
            assertThat(sshExecuteCommand.execute(client, "mysql -N -e \"SHOW DATABASES LIKE 'voice_asterisk'\"").trim())
                    .isEqualTo("voice_asterisk");
        }
    }
}
