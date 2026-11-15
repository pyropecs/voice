package com.voip.voice.ssh.services;

public class SSHCommandExecutor {

    private final SSHConnectionManager connectionManager;
    public SSHCommandExecutor(SSHConnectionManager connectionManager){
        this.connectionManager = connectionManager;
    }
  public String executeCommand(String host,int port,String username,String password){
 return "";
  }
}
