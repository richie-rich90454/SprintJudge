package com.sprintjudge.service.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class NsJailExecutorTest {

	private NsJailExecutor executor(String binary) throws Exception {
		NsJailExecutor ex = new NsJailExecutor();
		Field f = NsJailExecutor.class.getDeclaredField("nsjailBinary");
		f.setAccessible(true);
		f.set(ex, binary);
		return ex;
	}

	@Test
	void missingBinaryAbortsBoot(@TempDir Path tmp) throws Exception {
		NsJailExecutor ex = executor(tmp.resolve("no-nsjail-here").toString());
		IllegalStateException e = assertThrows(IllegalStateException.class,
				new org.junit.jupiter.api.function.Executable() {
					@Override
					public void execute() throws java.lang.Throwable {
						ex.verifyBinary();
					}
				});
		assertTrue(e.getMessage().contains("not executable"));
	}

	@Test
	void presentBinaryPasses(@TempDir Path tmp) throws Exception {
		Path fake = tmp.resolve("nsjail");
		Files.writeString(fake, "#!/bin/sh");
		fake.toFile().setExecutable(true);
		assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				executor(fake.toString()).verifyBinary();
			}
		});
	}
}
