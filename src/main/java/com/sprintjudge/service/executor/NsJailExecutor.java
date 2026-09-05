package com.sprintjudge.service.executor;

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
@ConditionalOnProperty(name = "sprintjudge.executor.mode", havingValue = "nsjail")
public class NsJailExecutor extends AbstractScriptExecutor {

    @Value("${sprintjudge.executor.nsjail-binary:/usr/bin/nsjail}")
    private String nsjailBinary;

    @Override
    protected List<String> commandFor(String language, Path sourceFile, Path inputFile, Path runDir) {
        Path scriptsDir = scriptPath(language).toAbsolutePath().getParent();
        List<String> cmd = new ArrayList<>(List.of(
                nsjailBinary,
                "-Mo", "--chroot", "/",
                "-E", "PATH=/usr/bin:/bin",
                "--rlimit_as", "512M",
                "--rlimit_cpu", "30",
                "--rlimit_fsize", "8",
                "--read_only",
                "--proc_rw",
                "-R", "/usr", "-R", "/lib", "-R", "/lib64", "-R", "/bin", "-R", "/etc/alternatives",
                // ponytail: the jail sees host-absolute argv paths, so the run
                // dir (sources, inputs, compiler outputs) must be mounted
                // writable and the scripts dir readable — otherwise every
                // jailed judge fails on invisible files. Tmpfs /tmp keeps
                // compiler temp files off the host disk.
                "--bindmount", runDir.toString() + ":" + runDir.toString(),
                "-R", scriptsDir.toString(),
                "--tmpfsmount", "/tmp",
                "--",
                "bash", scriptPath(language).toString(),
                sourceFile.toString(), inputFile.toString(), runDir.toString()));
        return cmd;
    }
}
