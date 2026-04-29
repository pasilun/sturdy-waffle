package com.sturdywaffle.eval;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// Boots the eval-profile Spring context and asserts it loads cleanly.
// Doesn't run any fixtures, doesn't call Anthropic. Catches the kind
// of regression that broke ./gradlew eval today: a circular bean cycle
// (evalRunner ↔ pipelineService ↔ noopPersister) that ./gradlew test
// never noticed because nothing exercised the eval profile context.
//
// If a future refactor reintroduces the cycle — or breaks any other
// eval-profile wiring — this test fails fast.
@SpringBootTest(classes = EvalRunner.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("eval")
@TestPropertySource(properties = {
        // AnthropicConfig requires the key to instantiate the client (no
        // network call at boot). Real key not needed for context startup.
        "ANTHROPIC_API_KEY=test-stub"
})
class EvalContextSmokeTest {

    @Autowired EvalRunner evalRunner;

    @Test
    void contextLoads() {
        assertNotNull(evalRunner, "eval profile context must wire EvalRunner cleanly");
    }
}
