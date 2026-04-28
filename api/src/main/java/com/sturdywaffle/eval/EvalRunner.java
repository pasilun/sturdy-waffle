package com.sturdywaffle.eval;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Phase 2: activated under the "eval" Spring profile by ./gradlew eval.
 * Loads fixtures from src/eval/fixtures/, runs PipelineService, prints results.
 */
@Component
@Profile("eval")
public class EvalRunner {

    public static void main(String[] args) {
        // Placeholder — full implementation in Phase 2
        System.out.println("Eval harness: Phase 2 not yet implemented");
    }
}
