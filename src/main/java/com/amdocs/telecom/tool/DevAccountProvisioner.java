package com.amdocs.telecom.tool;

import com.amdocs.telecom.security.Role;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Component
@Profile("dev")
public class DevAccountProvisioner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;
    public DevAccountProvisioner(JdbcTemplate jdbc, PasswordEncoder encoder, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.encoder = encoder; transaction = new TransactionTemplate(manager);
    }
    public void provision() throws Exception {
        Map<String, Role> accounts = new LinkedHashMap<>();
        accounts.put("customer.demo", Role.CUSTOMER);
        accounts.put("desk.demo", Role.SERVICE_DESK);
        accounts.put("engineer.demo", Role.NETWORK_ENGINEER);
        accounts.put("manager.demo", Role.NETWORK_MANAGER);
        Properties credentials = new Properties();
        SecureRandom random = new SecureRandom();
        for (String name : accounts.keySet()) {
            byte[] bytes = new byte[18]; random.nextBytes(bytes);
            credentials.setProperty(name + ".password", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        }
        Path path = Paths.get("config/dev-accounts.properties");
        Files.createDirectories(path.getParent());
        // Never overwrite a private file or silently reset existing accounts.
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            credentials.store(writer, "Local initial demo passwords. Do not share or commit. Password resets do not update this file.");
        }
        try {
            transaction.execute(status -> {
                for (Map.Entry<String, Role> entry : accounts.entrySet()) {
                    long id = jdbc.queryForObject("INSERT INTO app_user (username,display_name,email,password_hash) "
                                    + "VALUES (?,?,?,?) RETURNING user_id", Long.class, entry.getKey(),
                            entry.getValue().name().replace('_', ' '), entry.getKey() + "@example.test",
                            encoder.encode(credentials.getProperty(entry.getKey() + ".password")));
                    jdbc.update("INSERT INTO user_role (user_id,role_code) VALUES (?,?)", id, entry.getValue().name());
                    if (entry.getValue() == Role.CUSTOMER && jdbc.update(
                            "UPDATE customer SET user_id=? WHERE customer_number='CUST1001' AND user_id IS NULL", id) != 1) {
                        throw new IllegalStateException("Demo customer must exist and be unlinked");
                    }
                    if (entry.getValue() == Role.NETWORK_ENGINEER && jdbc.update(
                            "UPDATE network_engineer SET user_id=? WHERE employee_code='ENG1008' AND user_id IS NULL", id) != 1) {
                        throw new IllegalStateException("Demo engineer must exist and be unlinked");
                    }
                }
                return null;
            });
        } catch (RuntimeException exception) { Files.deleteIfExists(path); throw exception; }
    }
}
