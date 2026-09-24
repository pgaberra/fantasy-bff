package com.fantasy.bff.email;

public enum SignupMethod {
    EMAIL("Email and password"),
    GOOGLE("Google"),
    FACEBOOK("Facebook");

    private final String label;

    SignupMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
