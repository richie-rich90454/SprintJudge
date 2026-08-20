package com.openquiz.service.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Development executor: runs the compile scripts inside WSL2 (Ubuntu) on Windows.
 * The scripts themselves perform the compile/run against the input file.
 */
@Component
@Profile("dev")
public class WslExecutor extends AbstractScriptExecutor {

    @Value("${openquiz.executor.wsl-distro:Ubuntu}")
    private String wslDistro;

    @Override
    protected List<String> buildCommand(String scriptPath, String sourceFile, String inputFile, String runDir) {
        // WSL2: convert Windows paths to /mnt/... and run the bash script.
        String wslScript = toWslPath(scriptPath);
        String wslSource = toWslPath(sourceFile);
        String wslInput = toWslPath(inputFile);
        return Arrays.asList("wsl", "-d", wslDistro, "bash", wslScript, wslSource, wslInput, toWslPath(runDir));
    }

    private String toWslPath(String windowsPath) {
        String p = windowsPath.replace('\\', '/');
        if (p.matches("^[A-Za-z]:.*")) {
            String drive = p.substring(0, 1).toLowerCase();
            p = "/mnt/" + drive + p.substring(2);
        }
        return p;
    }
}
