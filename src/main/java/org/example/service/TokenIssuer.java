package org.example.service;

import org.example.dto.AccessToken;
import org.example.shared.api.AuthenticatedUser;

public interface TokenIssuer {
    AccessToken issue(AuthenticatedUser user);
}
