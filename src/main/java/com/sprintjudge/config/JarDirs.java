package com.sprintjudge.config;

import java.io.File;
import java.net.URI;

/**
 * Resolves well-known filesystem anchors: the folder containing the running
 * jar (or classes dir under Maven), and the active .env candidates.
 */
public final class JarDirs {

    private JarDirs() {}

    /** Directory of the running jar, or the project dir when launched from IDE/Maven. */
    public static File appDir() {
        try {
            String s = JarDirs.class.getProtectionDomain().getCodeSource().getLocation().toString();
            if (s.startsWith("jar:")) s = s.substring(4);
            if (s.startsWith("nested:")) s = s.substring("nested:".length());
            int bang = s.indexOf('!');
            if (bang >= 0) s = s.substring(0, bang);
            File codeSource = new File(URI.create(s));
            File dir = codeSource.isFile() ? codeSource.getParentFile() : codeSource;
            // Running unpacked (target/classes): fall back to working directory.
            if (dir != null && dir.getName().equals("classes")) {
                return new File(System.getProperty("user.dir"));
            }
            return dir == null ? new File(".") : dir;
        } catch (Exception e) {
            return new File(System.getProperty("user.dir"));
        }
    }
}
