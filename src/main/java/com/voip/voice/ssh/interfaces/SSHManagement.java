package com.voip.voice.ssh.interfaces;

import java.io.File;

public interface SSHManagement {
    boolean executeCommand(String command);
    boolean fileVMTransfer(File file);
}
