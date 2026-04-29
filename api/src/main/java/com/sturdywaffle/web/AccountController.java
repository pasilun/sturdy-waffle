package com.sturdywaffle.web;

import com.sturdywaffle.infrastructure.persistence.AccountQuery;
import com.sturdywaffle.web.dto.AccountResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@Profile("!eval")
public class AccountController {

    private final AccountQuery accountQuery;

    public AccountController(AccountQuery accountQuery) {
        this.accountQuery = accountQuery;
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accountQuery.listAll();
    }
}
