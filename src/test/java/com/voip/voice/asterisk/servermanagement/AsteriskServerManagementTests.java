package com.voip.voice.asterisk.servermanagement;

import com.voip.voice.VoiceApplication;
import com.voip.voice.asterisk.servermanagement.dtos.AsteriskServerDetails;
import com.voip.voice.asterisk.servermanagement.repositories.AsteriskServerRepository;
import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.enums.asterisk.AsteriskServerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AsteriskServerController.
 * Runs the full stack (controller -> service -> repository -> database).
 * Each test is rolled back thanks to @Transactional, so the DB is left untouched.
 * Security filters are disabled so the controller logic can be tested without a JWT.
 */
@SpringBootTest(classes = VoiceApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Transactional
public class AsteriskServerManagementTests {

    private static final String IP = "10.255.255.1";
    private static final String OTHER_IP = "10.255.255.2";
    private static final int NON_EXISTENT_ID = Integer.MAX_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsteriskServerRepository asteriskServerRepository;

    @Autowired
    private JsonMapper objectMapper;

    @BeforeEach
    void cleanTestIps() {
        asteriskServerRepository.findByIpAddress(IP).ifPresent(asteriskServerRepository::delete);
        asteriskServerRepository.findByIpAddress(OTHER_IP).ifPresent(asteriskServerRepository::delete);
        asteriskServerRepository.flush();
    }

    private AsteriskServer saveServer(String ip) {
        AsteriskServer server = new AsteriskServer();
        server.setIpAddress(ip);
        server.setSshUsername("root");
        server.setSshPassword("secret");
        server.setRealtimeDatabaseName("voice_asterisk");
        server.setRealtimeDatabaseUser("asterisk_voip");
        server.setRealtimeDatabasePassword("asterisk");
        server.setAsteriskServerState(AsteriskServerState.PENDING);
        return asteriskServerRepository.saveAndFlush(server);
    }

    private String json(AsteriskServerDetails details) {
        return objectMapper.writeValueAsString(details);
    }

    // ---------------- CREATE ----------------

    @DisplayName("Create: valid details persist a PENDING server with realtime DB defaults")
    @Test
    public void createAsteriskServerWithCorrectCredentials() throws Exception {
        mockMvc.perform(post("/server/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AsteriskServerDetails(IP, "root", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Asterisk server details created Successfully"));

        Optional<AsteriskServer> saved = asteriskServerRepository.findByIpAddress(IP);
        assertThat(saved).isPresent();
        assertThat(saved.get().getSshUsername()).isEqualTo("root");
        assertThat(saved.get().getSshPassword()).isEqualTo("secret");
        assertThat(saved.get().getAsteriskServerState()).isEqualTo(AsteriskServerState.PENDING);
        assertThat(saved.get().getRealtimeDatabaseName()).isEqualTo("voice_asterisk");
        assertThat(saved.get().getRealtimeDatabaseUser()).isEqualTo("asterisk_voip");
        assertThat(saved.get().getRealtimeDatabasePassword()).isEqualTo("asterisk");
    }

    @DisplayName("Create: duplicate IP address returns 400")
    @Test
    public void createAsteriskServerWithDuplicateIp() throws Exception {
        saveServer(IP);

        mockMvc.perform(post("/server/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AsteriskServerDetails(IP, "admin", "other"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Asterisk Server already exists"));

        assertThat(asteriskServerRepository.findByIpAddress(IP).get().getSshUsername()).isEqualTo("root");
    }

    @DisplayName("Create: missing required ssh username returns 500")
    @Test
    public void createAsteriskServerWithMissingUsername() throws Exception {
        mockMvc.perform(post("/server/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AsteriskServerDetails(IP, null, "secret"))))
                .andExpect(status().isInternalServerError());
    }

    // ---------------- UPDATE ----------------

    @DisplayName("Update: existing server is updated")
    @Test
    public void updateExistingAsteriskServer() throws Exception {
        AsteriskServer server = saveServer(IP);

        mockMvc.perform(post("/server/update/{id}", server.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AsteriskServerDetails(OTHER_IP, "admin", "newSecret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Asterisk server details updated Successfully"));

        AsteriskServer updated = asteriskServerRepository.findById(server.getId()).orElseThrow();
        assertThat(updated.getIpAddress()).isEqualTo(OTHER_IP);
        assertThat(updated.getSshUsername()).isEqualTo("admin");
        assertThat(updated.getSshPassword()).isEqualTo("newSecret");
    }

    @DisplayName("Update: unknown id returns 500 (service calls Optional.get() on empty)")
    @Test
    public void updateNonExistentAsteriskServer() throws Exception {
        mockMvc.perform(post("/server/update/{id}", NON_EXISTENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AsteriskServerDetails(IP, "root", "secret"))))
                .andExpect(status().isInternalServerError());
    }

    // ---------------- DELETE ----------------

    @DisplayName("Delete: existing server is removed")
    @Test
    public void deleteExistingAsteriskServer() throws Exception {
        AsteriskServer server = saveServer(IP);

        mockMvc.perform(delete("/server/delete/{id}", server.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Asterisk server details deleted Successfully"));

        assertThat(asteriskServerRepository.findById(server.getId())).isEmpty();
    }

    @DisplayName("Delete: unknown id returns 400")
    @Test
    public void deleteNonExistentAsteriskServer() throws Exception {
        mockMvc.perform(delete("/server/delete/{id}", NON_EXISTENT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Asterisk Server Not Found"));
    }

    // ---------------- GET BY ID ----------------

    @DisplayName("Get by id: existing server is returned")
    @Test
    public void getAsteriskServerById() throws Exception {
        AsteriskServer server = saveServer(IP);

        mockMvc.perform(get("/server/{id}", server.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ipAddress").value(IP))
                .andExpect(jsonPath("$.sshUsername").value("root"))
                .andExpect(jsonPath("$.sshPassword").value("secret"));
    }

    @DisplayName("Get by id: unknown id returns 404")
    @Test
    public void getAsteriskServerByIdNotFound() throws Exception {
        mockMvc.perform(get("/server/{id}", NON_EXISTENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Asterisk server not found"));
    }

    // ---------------- GET ALL ----------------

    @DisplayName("Get all: returns every saved server")
    @Test
    public void getAllAsteriskServers() throws Exception {
        saveServer(IP);
        saveServer(OTHER_IP);
        int total = (int) asteriskServerRepository.count();

        mockMvc.perform(get("/server/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(total))
                .andExpect(jsonPath("$[?(@.ipAddress == '" + IP + "')]").exists())
                .andExpect(jsonPath("$[?(@.ipAddress == '" + OTHER_IP + "')]").exists());
    }

    // ---------------- SEARCH BY IP ----------------

    @DisplayName("Search: existing IP returns the server")
    @Test
    public void searchAsteriskServerByIp() throws Exception {
        saveServer(IP);

        mockMvc.perform(get("/server/search").param("ipAddress", IP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ipAddress").value(IP))
                .andExpect(jsonPath("$.sshUsername").value("root"));
    }

    @DisplayName("Search: unknown IP returns 404")
    @Test
    public void searchAsteriskServerByUnknownIp() throws Exception {
        mockMvc.perform(get("/server/search").param("ipAddress", IP))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Asterisk server not found"));
    }

    @DisplayName("Search: missing ipAddress param returns 400")
    @Test
    public void searchAsteriskServerWithoutIpParam() throws Exception {
        mockMvc.perform(get("/server/search"))
                .andExpect(status().isBadRequest());
    }
}
