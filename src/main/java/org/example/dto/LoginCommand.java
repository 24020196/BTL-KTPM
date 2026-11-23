package org.example.dto;

public record LoginCommand(String email, String password) {
    @Override public String toString() { return "LoginCommand[REDACTED]"; }
}
