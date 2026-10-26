package com.example.musictournament.repo;

import com.example.musictournament.model.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
