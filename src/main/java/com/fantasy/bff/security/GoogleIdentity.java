package com.fantasy.bff.security;

/** The claims we trust from a verified Google ID token. */
public record GoogleIdentity(String sub, String email) {}
