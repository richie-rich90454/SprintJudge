package com.openquiz.service.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Production executor: wraps the compile scripts inside nsjail for CPU/RAM
 * isolation and chroot. The scripts do the actual compile + run.
 */
@Component
@Profile("!dev")
public class NsJailExecutor extends AbstractScriptExecutor {

    @Value("${openquiz.executor.nsjail-binary:/usr/bin/nsjail}")
    private String nsjailBinary;

    @Override
    protected List<String> buildCommand(String scriptPath, String sourceFile, String inputFile, String runDir) {
        int memMb = 256;
        return Arrays.asList(
                nsjailBinary,
                "-Mo", "--chroot", "/",
                "-E", "PATH=/usr/bin:/bin",
                "--rlimit_as", memMb + "M",
                "--rlimit_cpu", "10",
                "--read_only",
                "--proc_rw",
                "-R", "/usr", "-R", "/lib", "-R", "/lib64", "-R", "/bin", "-R", "/etc/alternatives",
                "--",
                "bash", scriptPath, sourceFile, inputFile, runDir);
    }
}
