package com.voip.voice.entities;

import com.voip.voice.enums.asterisk.AsteriskServerState;
import jakarta.persistence.*;

@Entity
@Table(name = "asterisk_servers")
public class AsteriskServer {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(unique = true,nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private String sshUsername;

    @Column(nullable = true)
    private String sshPassword;

    @Column(nullable = false)
    private String realtimeDatabaseName;

    @Column(nullable = false)
    private String realtimeDatabasePassword;

    @Column(nullable = false)
    private String realtimeDatabaseUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,name = "status")
    private AsteriskServerState asteriskServerState;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSshUsername() {
        return sshUsername;
    }

    public void setSshUsername(String sshUsername) {
        this.sshUsername = sshUsername;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public void setSshPassword(String sshPassword) {
        this.sshPassword = sshPassword;
    }

    public String getRealtimeDatabaseName() {
        return realtimeDatabaseName;
    }

    public void setRealtimeDatabaseName(String realtimeDatabaseName) {
        this.realtimeDatabaseName = realtimeDatabaseName;
    }

    public String getRealtimeDatabasePassword() {
        return realtimeDatabasePassword;
    }

    public void setRealtimeDatabasePassword(String realtimeDatabasePassword) {
        this.realtimeDatabasePassword = realtimeDatabasePassword;
    }

    public String getRealtimeDatabaseUser() {
        return realtimeDatabaseUser;
    }

    public void setRealtimeDatabaseUser(String realtimeDatabaseUser) {
        this.realtimeDatabaseUser = realtimeDatabaseUser;
    }

    public AsteriskServerState getAsteriskServerState() {
        return asteriskServerState;
    }

    public void setAsteriskServerState(AsteriskServerState asteriskServerState) {
        this.asteriskServerState = asteriskServerState;
    }
}
