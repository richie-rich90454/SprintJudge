package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class ClientIpResolverTest {

	@Test
	void remotePeerIgnoresProxyHeaders() {
		assertEquals("8.8.8.8", ClientIpResolver.resolve("8.8.8.8", "1.2.3.4", "5.6.7.8"));
	}

	@Test
	void loopbackUsesLastForwardedSegment() {
		assertEquals("5.6.7.8", ClientIpResolver.resolve("127.0.0.1", "1.2.3.4, 5.6.7.8", null));
		assertEquals("5.6.7.8", ClientIpResolver.resolve("::1", null, "5.6.7.8"));
		assertEquals("1.2.3.4", ClientIpResolver.resolve("0:0:0:0:0:0:0:1", "1.2.3.4", null));
		assertEquals("127.0.0.1", ClientIpResolver.resolve("127.0.0.1", null, null));
		assertEquals("unresolved", ClientIpResolver.resolve(null, null, null));
		assertTrue(ClientIpResolver.isLoopback("127.0.0.1"));
		assertEquals(false, ClientIpResolver.isLoopback("8.8.8.8"));
	}

	@Test
	void emptyHeaderSegmentsAreSkipped() {
		assertEquals("127.0.0.1", ClientIpResolver.resolve("127.0.0.1", "  ", "  "));
		assertEquals("127.0.0.1", ClientIpResolver.resolve("127.0.0.1", "", null));
		assertEquals("127.0.0.1", ClientIpResolver.resolve("127.0.0.1", ",", null));
		assertEquals("unresolved", ClientIpResolver.resolve(null, "1.2.3.4", null));
		assertEquals("9.9.9.9", ClientIpResolver.resolve("::1", "  ", "9.9.9.9"));
		assertEquals("8.8.8.8", ClientIpResolver.resolve("8.8.8.8", null, null));
		assertEquals("8.8.8.8", ClientIpResolver.resolve("8.8.8.8", "1.2.3.4", "5.6.7.8"));
	}
}
