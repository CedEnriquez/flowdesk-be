package com.cedrick.flowdeskbe.config;

import com.cedrick.flowdeskbe.entity.User;
import com.cedrick.flowdeskbe.enums.Role;
import com.cedrick.flowdeskbe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Demo users for local development. Disable with app.seed.enabled=false (and never use in production). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seed("admin@flowdesk.test", "Ada Admin", "Admin123!", Role.ADMIN);
        seed("alice@flowdesk.test", "Alice Requester", "Password123!", Role.REQUESTER);
        seed("bob@flowdesk.test", "Bob Requester", "Password123!", Role.REQUESTER);
    }

    private void seed(String email, String name, String rawPassword, Role role) {
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            userRepository.save(new User(email, name, passwordEncoder.encode(rawPassword), role));
            log.info("Seeded demo user {} ({})", email, role);
        }
    }
}
