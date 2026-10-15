package com.x8ing.mtl.server.mtlserver.trackreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercise the actual Maven lifecycle in a disposable project containing only core code and a synthetic caller.
 */
@EnabledIfSystemProperty(named = "mtl.test.jpms.build", matches = "true")
class GpsQualityModuleBuildIntegrationTest {
    @TempDir
    Path temporary;

    @Test
    void nonCleanBuildRechecksConsumersWhenAModuleApiChanges() throws Exception {
        Path project = Files.createDirectory(temporary.resolve("mtl-server"));
        Files.copy(Path.of("../pom.xml"), temporary.resolve("pom.xml"));
        Files.copy(Path.of("pom.xml"), project.resolve("pom.xml"));
        copyTree(Path.of("src/main/modules"), project.resolve("src/main/modules"));
        Path core = Path.of("src/main/java/com/x8ing/mtl/server/gpsquality");
        copyTree(core, project.resolve(core));
        Path document = project.resolve(core).resolve("diagnostics/GpxDocument.java");
        String original = Files.readString(document);
        String baseline = original.substring(0, original.lastIndexOf('}'))
                          + "public static int jpmsBuildProbe() { return 1; }\n}\n";
        Files.writeString(document, baseline);
        Path consumer = project.resolve("src/main/java/architecture/probe/Consumer.java");
        Files.createDirectories(consumer.getParent());
        Files.writeString(consumer, """
                package architecture.probe;
                public class Consumer {
                    public int call() { return com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument.jpmsBuildProbe(); }
                }
                """);
        Path firstLog = temporary.resolve("baseline.log");
        assertEquals(0, compile(project, firstLog), () -> readLog(firstLog));
        Path resource = project.resolve("target/classes/preserve-resource.txt");
        Files.writeString(resource, "Keep non-class build resources.");
        Path obsoleteModule = project.resolve("target/gpsquality-classes/obsolete.module/Old.class");
        Files.createDirectories(obsoleteModule.getParent());
        Files.writeString(obsoleteModule, "Obsolete module bytecode.");
        Path obsoleteJar = project.resolve("target/gpsquality/obsolete-module.jar");
        Files.createDirectories(obsoleteJar.getParent());
        Files.writeString(obsoleteJar, "Obsolete packaged module.");
        Files.writeString(document, baseline.replace("int jpmsBuildProbe()", "int renamedBuildProbe()"));
        Path secondLog = temporary.resolve("changed-api.log");
        assertNotEquals(0, compile(project, secondLog), "An unchanged caller of a removed core API must fail compilation.");
        assertTrue(readLog(secondLog).contains("jpmsBuildProbe"), () -> readLog(secondLog));
        assertTrue(Files.exists(resource), "Consumer recompilation must preserve resources.");
        assertFalse(Files.exists(obsoleteModule), "Removed module bytecode must not survive an incremental build.");
        assertFalse(Files.exists(obsoleteJar), "Removed module jars must not be copied into packaged images.");
    }

    private int compile(Path project, Path log) throws Exception {
        Process process = new ProcessBuilder("mvn", "-B", "compile", "-DskipTests")
                .directory(project.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Disposable Maven build timed out: " + log);
            return process.exitValue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path destination = target.resolve(source.relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(destination);
                else Files.copy(file, destination);
            }
        }
    }

    private static String readLog(Path log) {
        try {
            return Files.readString(log);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Cannot read module-build test log " + log, e);
        }
    }
}
