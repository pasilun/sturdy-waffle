package com.sturdywaffle.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class ChartSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChartSeeder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/seed/chart.json")) {
            List<Map<String, String>> accounts = objectMapper.readValue(is, new TypeReference<>() {});
            for (Map<String, String> account : accounts) {
                jdbcTemplate.update(
                        "INSERT INTO accounts (code, name, type, normal_side) VALUES (?,?,?,?) " +
                        "ON CONFLICT (code) DO NOTHING",
                        account.get("code"),
                        account.get("name"),
                        account.get("type"),
                        account.get("normalSide")
                );
            }
        }
    }
}
