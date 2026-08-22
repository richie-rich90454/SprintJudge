package com.openquiz.service.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Development executor: runs the compile scripts inside WSL2 (Ubuntu) on Windows.
 * The scripts themselves perform the compile/run against the input file.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "openquiz.executor.mode", havingValue = "wsl")
public class WslExecutor extends AbstractScriptExecutor {

    @Value("${openquiz.executor.wsl-distro:Ubuntu}")
    private String wslDistro;

    @Override
    protected List<String> commandFor(String language, Path sourceFile, Path inputFile, Path runDir) {
        return Arrays.asList("wsl", "-d", wslDistro, "bash",
                toWsl(scriptPath(language).toString()),
                toWsl(sourceFile.toString()),
                toWsl(inputFile.toString()),
                toWsl(runDir.toString()));
    }

    private String toWsl(String windowsPath) {
        String p = windowsPath.replace('\\', '/');
        if (p.matches("^[A-Za-z]:.*")) {
            String drive = p.substring(0, 1).toLowerCase();
            p = "/mnt/" + drive + p.substring(2);
        }
        return p;
    }
}
