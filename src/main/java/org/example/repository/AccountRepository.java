package org.example.repository;

import java.util.Optional;
import org.example.dto.AccountCredential;
import org.example.dto.AccountRecord;
import org.example.dto.NewCustomer;

public interface AccountRepository {
    boolean existsByEmailOrPhone(String email, String phone);
    AccountRecord createCustomer(NewCustomer input);
    Optional<AccountCredential> findCredentialByEmail(String email);
}
