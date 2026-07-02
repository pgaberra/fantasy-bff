package com.fantasy.bff.security;

/** The identity we trust from a verified Facebook access token. */
public record FacebookIdentity(String sub, String email) {}
