package com.sprintjudge.auth;

/** Supplies the basis-auth JWKS document. Kept injectable for tests. */
public interface BasisJwkSource {
	String load();
}
