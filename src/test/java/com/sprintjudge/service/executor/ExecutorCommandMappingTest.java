package com.sprintjudge.service.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class ExecutorCommandMappingTest {

	private static NsJailExecutor nsjail(Path scripts, String binary) {
		NsJailExecutor ex = new NsJailExecutor();
		ReflectionTestUtils.setField(ex, "scriptsDir", scripts.toString());
		ReflectionTestUtils.setField(ex, "nsjailBinary", binary);
		return ex;
	}

	@Test
	void nsjailCommandHead(@TempDir Path tmp) {
		NsJailExecutor ex = nsjail(tmp, "/usr/bin/nsjail");
		List<String> cmd = ex.commandFor("python", tmp.resolve("solution.py").toAbsolutePath(),
				tmp.resolve("input_0.txt").toAbsolutePath(), tmp.toAbsolutePath());
		assertEquals("/usr/bin/nsjail", cmd.get(0));
		assertTrue(cmd.contains("--chroot"));
		assertTrue(cmd.contains("--read_only"));
		assertTrue(cmd.contains("--disable_proc"));
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
		assertEquals("PATH=/usr/bin:/bin:/usr/local/bin", cmd.get(e + 1));
	}
}
