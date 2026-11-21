package com.voip.voice.asterisk.orchestration.services;


import com.voip.voice.entities.AsteriskServer;
import com.voip.voice.ssh.sshmanagement.SFTPFileTransfer;
import com.voip.voice.ssh.sshmanagement.SSHExecuteCommand;
import net.schmizz.sshj.SSHClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class AsteriskConfigurationService {

    private final SSHExecuteCommand sshExecuteCommand;
    private final SFTPFileTransfer sftpFileTransfer;
    public AsteriskConfigurationService(SSHExecuteCommand sshExecuteCommand,SFTPFileTransfer sftpFileTransfer) {
        this.sshExecuteCommand = sshExecuteCommand;
        this.sftpFileTransfer = sftpFileTransfer;
    }

    public boolean configureAsteriskServer(SSHClient sshClient,AsteriskServer asteriskServer)  {
        try{
            getFileFromResourcesAndSendItToTheServer(sshClient,asteriskServer);
            sshExecuteCommand.execute(sshClient,"cd /home");
            String scriptArguements = asteriskServer.getIpAddress() + " " + asteriskServer.getRealtimeDatabaseName() + " " + asteriskServer.getRealtimeDatabaseUser() + " " + asteriskServer.getRealtimeDatabasePassword();
            String result = sshExecuteCommand.execute(sshClient,"./asterisk-realtime-setup.sh " + scriptArguements);
            System.out.println(result);
            return true;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return false;
        }

    }

private void getFileFromResourcesAndSendItToTheServer(SSHClient sshClient, AsteriskServer asteriskServer) throws IOException {
    ClassPathResource classPathResource = new ClassPathResource("scripts/asterisk-realtime-setup.sh");
    try(InputStream in = classPathResource.getInputStream()){
        sftpFileTransfer.upload(sshClient,in,"/home");
    }catch(Exception e){
        System.err.println("Failed to upload Asterisk Setup Script" + e.getMessage());
    }


}

}
