package org.example.service;

public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String passwordHash);
}
