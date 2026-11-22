package com.voip.voice.asterisk.orchestration;

import com.voip.voice.VoiceApplication;
import com.voip.voice.asterisk.orchestration.services.AsteriskConfigurationService;
import com.voip.voice.asterisk.orchestration.services.AsteriskOrchestrationService;
import com.voip.voice.asterisk.servermanagement.repositories.AsteriskServerRepository;
import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.enums.asterisk.AsteriskServerState;
import com.voip.voice.ssh.dto.SSHCredentiailsConfiguration;
import com.voip.voice.ssh.sshmanagement.SSHConnectionManagement;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.userauth.UserAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the orchestration flow
 * PENDING -> (SSH connect) -> CONFIGURING -> (run setup script) -> CONFIGURED.
 *
 * Uses the real service + repository + database; only the SSH connection and the
 * remote configuration step are mocked. Every test is rolled back.
 */
@SpringBootTest(classes = VoiceApplication.class)
@Transactional
class AsteriskOrchestrationFlowTests {

    private static final String IP = "10.255.254.1";
    private static final String OTHER_IP = "10.255.254.2";

    @Autowired
    private AsteriskOrchestrationService asteriskOrchestrationService;

    @Autowired
    private AsteriskServerRepository asteriskServerRepository;

    @MockitoBean
    private SSHConnectionManagement sshConnectionManagement;

    @MockitoBean
    private AsteriskConfigurationService asteriskConfigurationService;

    private final SSHClient sshClient = mock(SSHClient.class);

    @BeforeEach
    void isolateFromExistingData() {
        // Real PENDING rows in the DB would be picked first; park them (rolled back after the test).
        asteriskServerRepository.findAll().stream()
                .filter(s -> s.getAsteriskServerState() == AsteriskServerState.PENDING)
                .forEach(s -> s.setAsteriskServerState(AsteriskServerState.CONFIGURED));
        asteriskServerRepository.findByIpAddress(IP).ifPresent(asteriskServerRepository::delete);
        asteriskServerRepository.findByIpAddress(OTHER_IP).ifPresent(asteriskServerRepository::delete);
        asteriskServerRepository.flush();
    }

    private AsteriskServer saveServer(String ip, AsteriskServerState state) {
        AsteriskServer server = new AsteriskServer();
        server.setIpAddress(ip);
        server.setSshUsername("root");
        server.setSshPassword("secret");
        server.setRealtimeDatabaseName("voice_asterisk");
        server.setRealtimeDatabaseUser("asterisk_voip");
        server.setRealtimeDatabasePassword("asterisk");
        server.setAsteriskServerState(state);
        return asteriskServerRepository.saveAndFlush(server);
    }

    private AsteriskServerState stateOf(AsteriskServer server) {
        asteriskServerRepository.flush(); // push pending updates to the DB so column constraints are exercised
        return asteriskServerRepository.findById(server.getId()).orElseThrow().getAsteriskServerState();
    }

    @DisplayName("No PENDING server: nothing is contacted")
    @Test
    void noPendingServer_doesNothing() throws Exception {
        saveServer(IP, AsteriskServerState.CONFIGURED);

        asteriskOrchestrationService.processPendingAsteriskServer();

        verifyNoInteractions(sshConnectionManagement, asteriskConfigurationService);
    }

    @DisplayName("PENDING server: connects with its SSH credentials, is CONFIGURING during setup, ends CONFIGURED")
    @Test
    void pendingServer_isConfigured() throws Exception {
        AsteriskServer server = saveServer(IP, AsteriskServerState.PENDING);
        when(sshConnectionManagement.connect(any())).thenReturn(sshClient);

        AtomicReference<AsteriskServerState> stateDuringConfiguration = new AtomicReference<>();
        when(asteriskConfigurationService.configureAsteriskServer(eq(sshClient), any())).thenAnswer(inv -> {
            stateDuringConfiguration.set(stateOf(server));
            return true;
        });

        asteriskOrchestrationService.processPendingAsteriskServer();

        verify(sshConnectionManagement).connect(new SSHCredentiailsConfiguration(IP, "root", "secret"));
        assertThat(stateDuringConfiguration.get()).isEqualTo(AsteriskServerState.CONFIGURING);
        assertThat(stateOf(server)).isEqualTo(AsteriskServerState.CONFIGURED);
        verify(sshClient).close();
    }

    @DisplayName("Setup script fails: server stays CONFIGURING and is not retried")
    @Test
    void configurationFails_serverStaysConfiguring() throws Exception {
        AsteriskServer server = saveServer(IP, AsteriskServerState.PENDING);
        when(sshConnectionManagement.connect(any())).thenReturn(sshClient);
        when(asteriskConfigurationService.configureAsteriskServer(any(), any())).thenReturn(false);

        asteriskOrchestrationService.processPendingAsteriskServer();
        asteriskOrchestrationService.processPendingAsteriskServer(); // next tick

        assertThat(stateOf(server)).isEqualTo(AsteriskServerState.CONFIGURING);
        verify(asteriskConfigurationService, times(1)).configureAsteriskServer(any(), any());
    }

    @DisplayName("Only server unreachable: nothing configured, server stays PENDING for the next tick")
    @Test
    void sshConnectionFails_serverStaysPending() throws Exception {
        AsteriskServer server = saveServer(IP, AsteriskServerState.PENDING);
        when(sshConnectionManagement.connect(any())).thenThrow(new IOException("Connection refused"));

        assertThat(asteriskOrchestrationService.processPendingAsteriskServer()).isEmpty();

        assertThat(stateOf(server)).isEqualTo(AsteriskServerState.PENDING);
        verifyNoInteractions(asteriskConfigurationService);
    }

    @DisplayName("First server unreachable: moves on and configures the next reachable server")
    @Test
    void unreachableServer_isSkipped_nextServerConfigured() throws Exception {
        AsteriskServer unreachable = saveServer(IP, AsteriskServerState.PENDING);
        AsteriskServer reachable = saveServer(OTHER_IP, AsteriskServerState.PENDING);
        when(sshConnectionManagement.connect(new SSHCredentiailsConfiguration(IP, "root", "secret")))
                .thenThrow(new IOException("Connection timed out"));
        when(sshConnectionManagement.connect(new SSHCredentiailsConfiguration(OTHER_IP, "root", "secret")))
                .thenReturn(sshClient);
        when(asteriskConfigurationService.configureAsteriskServer(eq(sshClient), any())).thenReturn(true);

        assertThat(asteriskOrchestrationService.processPendingAsteriskServer())
                .get().extracting(AsteriskServer::getIpAddress).isEqualTo(OTHER_IP);

        assertThat(stateOf(unreachable)).isEqualTo(AsteriskServerState.PENDING);
        assertThat(stateOf(reachable)).isEqualTo(AsteriskServerState.CONFIGURED);
        InOrder order = inOrder(sshConnectionManagement);
        order.verify(sshConnectionManagement).connect(new SSHCredentiailsConfiguration(IP, "root", "secret"));
        order.verify(sshConnectionManagement).connect(new SSHCredentiailsConfiguration(OTHER_IP, "root", "secret"));
    }

    @DisplayName("Login rejected (wrong credentials) is treated as unreachable and skipped")
    @Test
    void authFailure_isSkipped() throws Exception {
        AsteriskServer badCredentials = saveServer(IP, AsteriskServerState.PENDING);
        AsteriskServer good = saveServer(OTHER_IP, AsteriskServerState.PENDING);
        when(sshConnectionManagement.connect(new SSHCredentiailsConfiguration(IP, "root", "secret")))
                .thenThrow(new UserAuthException("Exhausted available authentication methods"));
        when(sshConnectionManagement.connect(new SSHCredentiailsConfiguration(OTHER_IP, "root", "secret")))
                .thenReturn(sshClient);
        when(asteriskConfigurationService.configureAsteriskServer(any(), any())).thenReturn(true);

        asteriskOrchestrationService.processPendingAsteriskServer();

        assertThat(stateOf(badCredentials)).isEqualTo(AsteriskServerState.PENDING);
        assertThat(stateOf(good)).isEqualTo(AsteriskServerState.CONFIGURED);
    }

    @DisplayName("All servers unreachable: every server is tried once, all stay PENDING")
    @Test
    void allUnreachable_allTried_allStayPending() throws Exception {
        AsteriskServer first = saveServer(IP, AsteriskServerState.PENDING);
        AsteriskServer second = saveServer(OTHER_IP, AsteriskServerState.PENDING);
        when(sshConnectionManagement.connect(any())).thenThrow(new IOException("No route to host"));

        assertThat(asteriskOrchestrationService.processPendingAsteriskServer()).isEmpty();

        verify(sshConnectionManagement, times(2)).connect(any());
        assertThat(stateOf(first)).isEqualTo(AsteriskServerState.PENDING);
        assertThat(stateOf(second)).isEqualTo(AsteriskServerState.PENDING);
        verifyNoInteractions(asteriskConfigurationService);
    }

    @DisplayName("Unreachable server is retried on the next tick once it comes back")
    @Test
    void unreachableServer_retriedNextTick() throws Exception {
        AsteriskServer server = saveServer(IP, AsteriskServerState.PENDING);
        when(sshConnectionManagement.connect(any()))
                .thenThrow(new IOException("Connection refused"))
                .thenReturn(sshClient);
        when(asteriskConfigurationService.configureAsteriskServer(any(), any())).thenReturn(true);

        asteriskOrchestrationService.processPendingAsteriskServer();
        assertThat(stateOf(server)).isEqualTo(AsteriskServerState.PENDING);

        asteriskOrchestrationService.processPendingAsteriskServer();
        assertThat(stateOf(server)).isEqualTo(AsteriskServerState.CONFIGURED);
    }

    @DisplayName("Several PENDING servers: one per run, oldest first")
    @Test
    void multiplePendingServers_oldestFirst() throws Exception {
        AsteriskServer first = saveServer(IP, AsteriskServerState.PENDING);
        AsteriskServer second = saveServer(OTHER_IP, AsteriskServerState.PENDING);
        when(sshConnectionManagement.connect(any())).thenReturn(sshClient);
        when(asteriskConfigurationService.configureAsteriskServer(any(), any())).thenReturn(true);

        asteriskOrchestrationService.processPendingAsteriskServer();

        assertThat(stateOf(first)).isEqualTo(AsteriskServerState.CONFIGURED);
        assertThat(stateOf(second)).isEqualTo(AsteriskServerState.PENDING);

        asteriskOrchestrationService.processPendingAsteriskServer();

        assertThat(stateOf(second)).isEqualTo(AsteriskServerState.CONFIGURED);
    }

    @DisplayName("setAsteriskServerInProcess: unknown id returns false")
    @Test
    void setInProcess_unknownId() {
        assertThat(asteriskOrchestrationService.setAsteriskServerInProcess(Integer.MAX_VALUE)).isFalse();
    }
}
