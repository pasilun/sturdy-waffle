package com.sturdywaffle.infrastructure.persistence;

import com.sturdywaffle.web.dto.AccountResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!eval")
public class AccountQuery {

    private final JdbcTemplate jdbc;

    public AccountQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AccountResponse> listAll() {
        return jdbc.query("""
                SELECT code, name, type, normal_side FROM accounts ORDER BY code
                """,
                (rs, rowNum) -> new AccountResponse(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("normal_side")));
    }
}
