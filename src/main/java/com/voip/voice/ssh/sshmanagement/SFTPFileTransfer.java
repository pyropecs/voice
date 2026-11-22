package com.voip.voice.ssh.sshmanagement;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.xfer.InMemorySourceFile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class SFTPFileTransfer {

    public void upload(
            SSHClient ssh,
            String localPath,
            String remotePath
    ) throws IOException {

        try (SFTPClient sftp = ssh.newSFTPClient()) {
            sftp.put(localPath, remotePath);
        }
    }


    public void upload(
            SSHClient ssh,
            InputStream inputStream,
            String remotePath
    ) throws IOException {

        byte[] content = inputStream.readAllBytes();
        String fileName = remotePath.substring(remotePath.lastIndexOf('/') + 1);

        try (SFTPClient sftp = ssh.newSFTPClient()) {
            sftp.put(new InMemorySourceFile() {
                @Override
                public String getName() {
                    return fileName;
                }

                @Override
                public long getLength() {
                    return content.length;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(content);
                }
            }, remotePath);
        }
    }


}
