package com.openquiz.service.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Production executor: wraps the compile scripts inside nsjail for CPU/RAM
 * isolation and chroot. The scripts do the actual compile + run.
 */
@Component
@Profile("!dev")
@ConditionalOnProperty(name = "openquiz.executor.mode", havingValue = "nsjail")
public class NsJailExecutor extends AbstractScriptExecutor {

    @Value("${openquiz.executor.nsjail-binary:/usr/bin/nsjail}")
    private String nsjailBinary;

    @Override
    protected List<String> commandFor(String language, Path sourceFile, Path inputFile, Path runDir) {
        List<String> cmd = new ArrayList<>(List.of(
                nsjailBinary,
                "-Mo", "--chroot", "/",
                "-E", "PATH=/usr/bin:/bin",
                "--rlimit_as", "256M",
                "--rlimit_cpu", "10",
                "--read_only",
                "--proc_rw",
                "-R", "/usr", "-R", "/lib", "-R", "/lib64", "-R", "/bin", "-R", "/etc/alternatives",
                "--",
                "bash", scriptPath(language).toString(),
                sourceFile.toString(), inputFile.toString(), runDir.toString()));
        return cmd;
    }
}
