package com.example.emailsystem.repository;

import com.example.emailsystem.domain.EmailMessage;
import com.example.emailsystem.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    List<EmailMessage> findByOwnerOrderByMessageDateDescCreatedAtDesc(User owner);

    boolean existsByOwnerAndProviderMessageId(User owner, String providerMessageId);
}
