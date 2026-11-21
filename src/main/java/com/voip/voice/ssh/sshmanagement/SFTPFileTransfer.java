package com.voip.voice.ssh.sshmanagement;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;
import java.io.InputStream;

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

        try (SFTPClient sftp = ssh.newSFTPClient()) {
            sftp.put(inputStream.toString(), remotePath);
        }
    }


}
