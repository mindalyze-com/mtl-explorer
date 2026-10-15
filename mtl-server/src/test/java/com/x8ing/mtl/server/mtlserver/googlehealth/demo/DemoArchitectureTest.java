package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DemoArchitectureTest {
    @Test
    void ordinaryHealthCodeHasNoGeneratorDependencyOrStagingQueries() throws Exception {
        Path health = Path.of("src/main/java/com/x8ing/mtl/server/mtlserver/googlehealth");
        try (var paths = Files.walk(health)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().startsWith(".") && !p.startsWith(health.resolve("demo"))).toList())
                assertThat(Files.readString(path)).as(path.toString()).doesNotContain("googlehealth.demo", "demo_health.", "DemoHealth");
        }
    }

    @Test
    void generatorHasNoSeparateDatabaseSchemaOrCheckpointTables() throws Exception {
        Path demo = Path.of("src/main/java/com/x8ing/mtl/server/mtlserver/googlehealth/demo");
        try (var paths = Files.walk(demo)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().startsWith(".")).toList())
                assertThat(Files.readString(path)).as(path.toString()).doesNotContain("demo_health.", "CREATE TABLE", "CREATE SCHEMA");
        }
        try (var paths = Files.walk(Path.of("src/main/resources/db/changelog"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".xml") && !p.getFileName().toString().startsWith(".")).toList())
                assertThat(Files.readString(path)).as(path.toString()).doesNotContain("demo_health.", "CREATE SCHEMA demo_health");
        }
    }
}
