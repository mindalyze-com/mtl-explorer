package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GpsQualityModuleIsolationTest {
    @TempDir
    Path temporary;

    @Test
    void testsRunTheActualNamedModulesWithTheIntendedDependencies() {
        GpsQualityModules.verify();
        Module diagnostics = TrackDiagnosticEngine.class.getModule();
        assertEquals(Set.of("java.base", "java.xml"), requires(diagnostics));
        assertFalse(diagnostics.canRead(getClass().getModule()));
        assertFalse(diagnostics.getDescriptor().isAutomatic());
    }

    @Test
    void compilerRejectsDiagnosticsDependingOnApplicationClasses() throws Exception {
        assertRejected(GpsQualityModules.DIAGNOSTICS_MODULE,
                "com.x8ing.mtl.server.mtlserver.trackreview.GpsQualityModules", "does not read");
    }

    private Set<String> requires(Module module) {
        return module.getDescriptor().requires().stream().map(ModuleDescriptor.Requires::name).collect(Collectors.toSet());
    }

    private void assertRejected(String module, String forbiddenType, String diagnostic) throws Exception {
        Path sourceRoot = Files.createTempDirectory(temporary, "forbidden-dependency-");
        Path probe = sourceRoot.resolve("ForbiddenDependency.java");
        Files.writeString(probe, "package architecture.probe; class ForbiddenDependency { " + forbiddenType + " forbidden; }");
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Run backend tests in the development container with its JDK.");
        var errors = new ByteArrayOutputStream();
        int result = compiler.run(null, null, errors,
                "--module-path", Path.of("target/gpsquality-classes").toAbsolutePath().toString(),
                "--add-modules", GpsQualityModules.DIAGNOSTICS_MODULE,
                "--patch-module", module + "=" + sourceRoot,
                "--class-path", Path.of("target/classes").toAbsolutePath().toString(),
                "-proc:none", "-d", sourceRoot.resolve("classes").toString(), probe.toString());
        String message = errors.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, result, "Forbidden dependency unexpectedly compiled: " + module + " -> " + forbiddenType);
        assertTrue(message.contains(diagnostic), message);
    }
}
