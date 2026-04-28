package com.sturdywaffle.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sturdywaffle.application.EvalResult;
import com.sturdywaffle.application.PipelineService;
import com.sturdywaffle.domain.model.Posting;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

@SpringBootApplication(scanBasePackages = "com.sturdywaffle")
@Component
@Profile("eval")
public class EvalRunner {

    record ExpectedFixture(String netTotal, String vatTotal, String grossTotal, List<ExpectedLine> lines) {
        record ExpectedLine(String accountCode) {}
    }

    private final PipelineService pipeline;
    private final ObjectMapper objectMapper;

    public EvalRunner(PipelineService pipeline, ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
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

        System.out.printf("%-25s  %-10s  %-8s  %-12s  %s%n",
                "case", "extract", "map", "confidence", "latency");
        System.out.println("-".repeat(65));

        int passed = 0, failed = 0;

        for (Path fixture : fixtures) {
            String name = fixture.getFileName().toString();
            String base = name.replaceFirst("\\.pdf$", "");
            Path expectedPath = fixtureDir.resolve(base + ".expected.json");

            try {
                byte[] pdf = Files.readAllBytes(fixture);
                EvalResult result = pipeline.evaluate(pdf);

                String mapResult = "-";
                if (Files.exists(expectedPath)) {
                    ExpectedFixture expected = objectMapper.readValue(expectedPath.toFile(), ExpectedFixture.class);
                    List<Posting> costPostings = result.postings().stream()
                            .filter(p -> p.lineIndex() >= 0)
                            .sorted(Comparator.comparingInt(Posting::lineIndex))
                            .toList();
                    int correct = 0;
                    int total = expected.lines().size();
                    for (int i = 0; i < Math.min(costPostings.size(), total); i++) {
                        if (costPostings.get(i).accountCode().equals(expected.lines().get(i).accountCode())) {
                            correct++;
                        }
                    }
                    mapResult = correct + "/" + total;
                }

                OptionalDouble avgConf = result.postings().stream()
                        .filter(p -> p.confidence() != null)
                        .mapToDouble(Posting::confidence)
                        .average();
                String conf = avgConf.isPresent() ? String.format("%.2f", avgConf.getAsDouble()) : "-";
                String latency = String.format("%.1fs", result.latencyMs() / 1000.0);

                System.out.printf("%-25s  %-10s  %-8s  %-12s  %s%n",
                        name, "PASS", mapResult, conf, latency);
                passed++;
            } catch (Exception e) {
                System.out.printf("%-25s  %-10s  %s: %s%n", name, "FAIL",
                        e.getClass().getSimpleName(), e.getMessage());
                Throwable cause = e.getCause();
                while (cause != null) {
                    System.out.println("  → " + cause);
                    cause = cause.getCause();
                }
                failed++;
            }
        }

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }
}
