package com.sprintjudge.util;

/** Resolves a client IP without trusting attacker-controlled proxy headers. */
public final class ClientIpResolver {

	private ClientIpResolver() {
	}

	/**
	 * Proxy headers are honoured only when the TCP peer is a loopback proxy (nginx
	 * on the same host). Direct-to-jar deployments therefore cannot be bypassed by
	 * rotating X-Forwarded-For. nginx appends the real peer as the LAST XFF
	 * segment, so earlier (client-controlled) segments are discarded.
	 */
	public static String resolve(String remoteAddr, String xForwardedFor, String xRealIp) {
		if (!isLoopback(remoteAddr))
			return remoteAddr == null ? "unresolved" : remoteAddr;
		String last = lastSegment(xForwardedFor);
		if (last != null && !last.isEmpty())
			return last;
		if (xRealIp != null && !xRealIp.trim().isEmpty())
			return xRealIp.trim();
		// remoteAddr is non-null here: a null peer was handled above.
		return remoteAddr;
	}

	public static boolean isLoopback(String address) {
		return "127.0.0.1".equals(address) || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address);
	}

	private static String lastSegment(String header) {
		if (header == null)
			return null;
		String[] parts = header.split(",");
		return parts.length == 0 ? null : parts[parts.length - 1].trim();
	}
}
