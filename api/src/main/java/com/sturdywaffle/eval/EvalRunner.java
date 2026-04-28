package com.sturdywaffle.eval;

import com.sturdywaffle.application.PipelineService;
import com.sturdywaffle.domain.model.SuggestionId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication(scanBasePackages = "com.sturdywaffle")
@Component
@Profile("eval")
public class EvalRunner {

    private final PipelineService pipeline;

    public EvalRunner(PipelineService pipeline) {
        this.pipeline = pipeline;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "eval");
        SpringApplication app = new SpringApplication(EvalRunner.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        try (ConfigurableApplicationContext ctx = app.run(args)) {
            ctx.getBean(EvalRunner.class).run();
        }
    }

    public void run() {
        Path fixtureDir = Paths.get("src/eval/fixtures");
        List<Path> fixtures;
        try {
            fixtures = Files.list(fixtureDir)
                    .filter(p -> p.toString().endsWith(".pdf"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            System.err.println("Cannot list fixtures: " + e.getMessage());
            return;
        }

        if (fixtures.isEmpty()) {
            System.out.println("No PDF fixtures found in " + fixtureDir);
            return;
        }

        int passed = 0;
        int failed = 0;

        for (Path fixture : fixtures) {
            System.out.printf("%-40s ", fixture.getFileName());
            try {
                byte[] pdf = Files.readAllBytes(fixture);
                SuggestionId id = pipeline.run(pdf, fixture.getFileName().toString());
                System.out.println("PASS  id=" + id.value());
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL  " + e.getClass().getSimpleName() + ": " + e.getMessage());
                Throwable cause = e.getCause();
                while (cause != null) { System.out.println("       → " + cause); cause = cause.getCause(); }
                failed++;
            }
        }

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }
}
