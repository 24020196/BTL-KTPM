package org.example.dto;

public record LoginResult(AccessToken token, AccountRecord user) {
    @Override public String toString() { return "LoginResult[REDACTED]"; }
}
