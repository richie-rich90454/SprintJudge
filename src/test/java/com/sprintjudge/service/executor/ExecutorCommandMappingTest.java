package com.sprintjudge.service.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorCommandMappingTest {

    private static WslExecutor wsl(Path scripts, String distro) {
        WslExecutor ex = new WslExecutor();
        ReflectionTestUtils.setField(ex, "scriptsDir", scripts.toString());
        ReflectionTestUtils.setField(ex, "wslDistro", distro);
        return ex;
    }

    private static NsJailExecutor nsjail(Path scripts, String binary) {
        NsJailExecutor ex = new NsJailExecutor();
        ReflectionTestUtils.setField(ex, "scriptsDir", scripts.toString());
        ReflectionTestUtils.setField(ex, "nsjailBinary", binary);
        return ex;
    }

    @Test
    void wslCommandShape(@TempDir Path tmp) {
        WslExecutor ex = wsl(tmp, "Ubuntu");
        Path src = tmp.resolve("solution.py").toAbsolutePath();
        Path in = tmp.resolve("input_0.txt").toAbsolutePath();
        List<String> cmd = ex.commandFor("python", src, in, tmp.toAbsolutePath());
        assertEquals(8, cmd.size());
        assertEquals("wsl", cmd.get(0));
        assertEquals("-d", cmd.get(1));
        assertEquals("Ubuntu", cmd.get(2));
        assertEquals("bash", cmd.get(3));
    }

    @Test
    void wslScriptPathIsAbsolutized(@TempDir Path tmp) {
        WslExecutor ex = wsl(tmp, "Ubuntu");
        Path in = tmp.resolve("i.txt").toAbsolutePath();
        List<String> cmd = ex.commandFor("node", tmp.resolve("solution.js").toAbsolutePath(), in,
                tmp.toAbsolutePath());
        assertTrue(cmd.get(4).endsWith("node.sh"));
        assertTrue(cmd.get(4).startsWith("/mnt/") || cmd.get(4).startsWith("/"));
        assertTrue(!cmd.get(4).contains("\\"));
    }

    @Test
    void wslDriveLetterConverted(@TempDir Path tmp) {
        WslExecutor ex = wsl(Path.of("C:\\work\\scripts"), "Ubuntu");
        List<String> cmd = ex.commandFor("python",
                Path.of("C:\\work\\run\\solution.py"), Path.of("C:\\work\\run\\input_0.txt"),
                Path.of("C:\\work\\run"));
        assertTrue(cmd.get(4).startsWith("/mnt/c/work/scripts/python.sh"), cmd.get(4));
        assertTrue(cmd.get(5).startsWith("/mnt/c/work/run/solution.py"), cmd.get(5));
        assertTrue(cmd.get(6).startsWith("/mnt/c/work/run/input_0.txt"), cmd.get(6));
        assertTrue(cmd.get(7).startsWith("/mnt/c/work/run"), cmd.get(7));
    }

    @Test
    void wslLowercasesDrive(@TempDir Path tmp) {
        WslExecutor ex = wsl(Path.of("D:\\s"), "Debian");
        List<String> cmd = ex.commandFor("c",
                Path.of("D:\\r\\solution.c"), Path.of("D:\\r\\i.txt"), Path.of("D:\\r"));
        assertTrue(cmd.get(4).startsWith("/mnt/d/"), cmd.get(4));
        assertEquals("Debian", cmd.get(2));
    }

    @Test
    void wslPosixPathUnchanged(@TempDir Path tmp) {
        WslExecutor ex = wsl(Path.of("/opt/scripts"), "Ubuntu");
        List<String> cmd = ex.commandFor("java",
                Path.of("/opt/run/Main.java"), Path.of("/opt/run/in.txt"), Path.of("/opt/run"));
        assertTrue(cmd.get(4).endsWith("/opt/scripts/java.sh"), cmd.get(4));
        assertTrue(!cmd.get(4).contains("\\"));
        assertTrue(cmd.get(5).endsWith("/opt/run/Main.java"), cmd.get(5));
    }

    @Test
    void wslBackslashesBecomeSlashes(@TempDir Path tmp) {
        WslExecutor ex = wsl(Path.of("E:\\a\\b"), "Ubuntu");
        List<String> cmd = ex.commandFor("cpp",
                Path.of("E:\\a\\b\\solution.cpp"), Path.of("E:\\a\\b\\i.txt"), Path.of("E:\\a\\b"));
        assertTrue(cmd.get(5).equals("/mnt/e/a/b/solution.cpp"), cmd.get(5));
    }

    @Test
    void nsjailCommandHead(@TempDir Path tmp) {
        NsJailExecutor ex = nsjail(tmp, "/usr/bin/nsjail");
        List<String> cmd = ex.commandFor("python",
                tmp.resolve("solution.py").toAbsolutePath(),
                tmp.resolve("input_0.txt").toAbsolutePath(), tmp.toAbsolutePath());
        assertEquals("/usr/bin/nsjail", cmd.get(0));
        assertTrue(cmd.contains("--chroot"));
        assertTrue(cmd.contains("--read_only"));
        assertTrue(cmd.contains("--proc_rw"));
    }

    @Test
    void nsjailRlimits(@TempDir Path tmp) {
        NsJailExecutor ex = nsjail(tmp, "/usr/bin/nsjail");
        List<String> cmd = ex.commandFor("python", tmp.resolve("s.py").toAbsolutePath(),
                tmp.resolve("i.txt").toAbsolutePath(), tmp.toAbsolutePath());
        int as = cmd.indexOf("--rlimit_as");
        int cpu = cmd.indexOf("--rlimit_cpu");
        int fsize = cmd.indexOf("--rlimit_fsize");
        assertTrue(as > -1 && cpu > -1 && fsize > -1);
        assertEquals("512M", cmd.get(as + 1));
        assertEquals("30", cmd.get(cpu + 1));
        assertEquals("8", cmd.get(fsize + 1));
    }

    @Test
    void nsjailBindMountsRunDir(@TempDir Path tmp) {
        NsJailExecutor ex = nsjail(tmp, "/usr/bin/nsjail");
        Path run = tmp.toAbsolutePath();
        List<String> cmd = ex.commandFor("node", tmp.resolve("solution.js").toAbsolutePath(),
                tmp.resolve("i.txt").toAbsolutePath(), run);
        int bi = cmd.indexOf("--bindmount");
        assertTrue(bi > -1);
        assertEquals(run + ":" + run, cmd.get(bi + 1));
        int ti = cmd.indexOf("--tmpfsmount");
        assertEquals("/tmp", cmd.get(ti + 1));
    }

    @Test
    void nsjailScriptsDirReadOnly(@TempDir Path tmp) {
        NsJailExecutor ex = nsjail(tmp, "/usr/bin/nsjail");
        List<String> cmd = ex.commandFor("c", tmp.resolve("solution.c").toAbsolutePath(),
                tmp.resolve("i.txt").toAbsolutePath(), tmp.toAbsolutePath());
        assertTrue(cmd.contains(tmp.toAbsolutePath().toString()));
    }

    @Test
    void nsjailTailInvokesScript(@TempDir Path tmp) {
        NsJailExecutor ex = nsjail(tmp, "/usr/bin/nsjail");
        Path src = tmp.resolve("solution.py").toAbsolutePath();
        Path in = tmp.resolve("input_0.txt").toAbsolutePath();
        Path run = tmp.toAbsolutePath();
        List<String> cmd = ex.commandFor("python", src, in, run);
        int sep = cmd.indexOf("--");
        assertTrue(sep > -1);
        assertEquals("bash", cmd.get(sep + 1));
        assertTrue(cmd.get(sep + 2).endsWith("python.sh"));
        assertEquals(src.toString(), cmd.get(sep + 3));
        assertEquals(in.toString(), cmd.get(sep + 4));
        assertEquals(run.toString(), cmd.get(sep + 5));
    }

    @Test
    void nsjailCustomBinary(@TempDir Path tmp) {
        NsJailExecutor ex = nsjail(tmp, "/opt/nsjail/nsjail");
        List<String> cmd = ex.commandFor("java", tmp.resolve("Main.java").toAbsolutePath(),
                tmp.resolve("i.txt").toAbsolutePath(), tmp.toAbsolutePath());
        assertEquals("/opt/nsjail/nsjail", cmd.get(0));
    }

    @Test
    void nsjailPathEnv(@TempDir Path tmp) {
        NsJailExecutor ex = nsjail(tmp, "/usr/bin/nsjail");
        List<String> cmd = ex.commandFor("cpp", tmp.resolve("s.cpp").toAbsolutePath(),
                tmp.resolve("i.txt").toAbsolutePath(), tmp.toAbsolutePath());
        int e = cmd.indexOf("-E");
        assertEquals("PATH=/usr/bin:/bin", cmd.get(e + 1));
    }
}
