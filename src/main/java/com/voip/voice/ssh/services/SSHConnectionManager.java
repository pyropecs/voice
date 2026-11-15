package com.voip.voice.ssh.services;


import com.voip.voice.asterisk.asteriskserver.services.AsteriskServerService;
import com.voip.voice.entities.AsteriskServer;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SSHConnectionManager {
   private final AsteriskServerService asteriskServerService;

   public SSHConnectionManager(AsteriskServerService asteriskServerService){
       this.asteriskServerService = asteriskServerService;
   }
    public SSHClient connect(String host) throws IOException {
        AsteriskServer server =
        return ssh;

    }


}
