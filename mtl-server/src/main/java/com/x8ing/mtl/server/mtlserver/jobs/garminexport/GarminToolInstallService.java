package com.x8ing.mtl.server.mtlserver.jobs.garminexport;

import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Manages on-demand installation of Garmin CLI tools (gcexport and fit-export).
 *
 * <p>Both install scripts are idempotent: if the versioned venv directory already exists,
 * the script exits in milliseconds without doing any work.
 *
 * <p>{@link #ensureGcexportInstalled(String, StringBuilder)} is called before every export run
 * so the log clearly shows which version is in use and whether an install was needed.
 */
@Slf4j
@Component
public class GarminToolInstallService {

    // --- Config DB keys ---
    static final String DOMAIN1 = "GARMIN_TOOL";
    static final String DOMAIN2_GCEXPORT = "GCEXPORT";
    static final String DOMAIN2_FIT = "FIT_EXPORT";
    static final String DOMAIN3_ACTIVE_VERSION = "ACTIVE_VERSION";
    static final String DOMAIN3_ACTIVE_PROFILE = "ACTIVE_PROFILE";
    static final String DOMAIN3_ACTIVE_PACKAGES = "ACTIVE_PACKAGES";

    // Strict allowlists to prevent shell injection
    private static final Pattern GCEXPORT_VERSION_PATTERN = Pattern.compile("^v\\d+\\.\\d+(\\.\\d+)?$");
    private static final Pattern FIT_PROFILE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern PIP_TOKEN_PATTERN = Pattern.compile("^[a-zA-Z0-9_.=-]+$");

    @Value("${mtl.garmin-sync.install-gcexport-script}")
    private String installGcexportScript;

    @Value("${mtl.garmin-sync.install-fit-export-script}")
    private String installFitExportScript;

    @Value("${mtl.garmin-sync.gcexport-default-version}")
    private String gcexportDefaultVersion;

    @Value("${mtl.garmin-sync.fit-export-default-profile}")
    private String fitExportDefaultProfile;

    @Value("${mtl.garmin-sync.fit-export-default-packages}")
    private String fitExportDefaultPackages;

    private final ConfigRepository configRepository;
    private final GarminOperationLock operationLock;

    public GarminToolInstallService(ConfigRepository configRepository, GarminOperationLock operationLock) {
        this.configRepository = configRepository;
        this.operationLock = operationLock;
    }

    // -------------------------------------------------------------------------
    // Public API — called by GarminExporter before each export run
    // -------------------------------------------------------------------------

    /**
     * Looks up the active gcexport version from DB (seeding from default if absent),
     * runs the install script (fast venv-exists check), and logs the outcome clearly.
     *
     * @return the active version string, e.g. "v4.6.2"
     */
    public String ensureGcexportInstalled(StringBuilder programOutput) throws Exception {
        String version = getActiveGcexportVersion();
        logInfo("gcexport: active version from config = %s".formatted(version), programOutput);

        String venvPath = resolveGcexportVenvPath(version);
        boolean venvExists = Files.isDirectory(Paths.get(venvPath));

        if (venvExists) {
            logInfo("gcexport: venv already present at %s — no install needed. Ready to export.".formatted(venvPath), programOutput);
        } else {
            logInfo("gcexport: venv NOT found at %s — running install script now...".formatted(venvPath), programOutput);
        }

        runScript(List.of(installGcexportScript, version), programOutput);

        if (!venvExists) {
            logInfo("gcexport: install completed — venv now available at %s".formatted(venvPath), programOutput);
        }
        return version;
    }

    /**
     * Looks up the active fit-export profile and ensures its venv is installed.
     *
     * @return the active profile name, e.g. "default"
     */
    public String ensureFitExportInstalled(StringBuilder programOutput) throws Exception {
        String profile = getActiveFitExportProfile();
        String packages = getActiveFitExportPackages();
        logInfo("fit-export: active profile=%s packages=[%s]".formatted(profile, packages), programOutput);

        String venvPath = resolveFitExportVenvPath(profile);
        boolean venvExists = Files.isDirectory(Paths.get(venvPath));

        if (venvExists) {
            logInfo("fit-export: venv already present at %s — no install needed.".formatted(venvPath), programOutput);
        } else {
            logInfo("fit-export: venv NOT found at %s — running install script now...".formatted(venvPath), programOutput);
        }

        runScript(List.of(installFitExportScript, profile, packages), programOutput);

        if (!venvExists) {
            logInfo("fit-export: install completed — venv now available at %s".formatted(venvPath), programOutput);
        }
        return profile;
    }

    // -------------------------------------------------------------------------
    // Admin-triggered install (acquires shared lock, updates DB on success)
    // -------------------------------------------------------------------------

    public String installGcexport(String version) throws Exception {
        validateGcexportVersion(version);
        if (!operationLock.tryLock()) {
            throw new IllegalStateException("A Garmin operation is already running. Please wait and try again.");
        }
        try {
            log.info("Admin-triggered gcexport install: version={}", version);
            StringBuilder out = new StringBuilder();
            logInfo("Admin install: gcexport version=%s".formatted(version), out);
            try {
                runScript(List.of(installGcexportScript, version), out);
            } catch (RuntimeException scriptEx) {
                logInfo("INSTALL FAILED: " + scriptEx.getMessage(), out);
                log.error("gcexport install script failed for version={}", version, scriptEx);
                // Throw with full accumulated output as message so the caller can surface it
                throw new RuntimeException(out.toString(), scriptEx);
            }
            saveOrUpdate(DOMAIN1, DOMAIN2_GCEXPORT, DOMAIN3_ACTIVE_VERSION, version,
                    "Active gcexport version — set by runtime install");
            logInfo("gcexport active version updated to %s in DB.".formatted(version), out);
            return out.toString();
        } finally {
            operationLock.unlock();
        }
    }

    public String installFitExport(String profile, String packages) throws Exception {
        validateFitProfile(profile);
        validatePipPackages(packages);
        if (!operationLock.tryLock()) {
            throw new IllegalStateException("A Garmin operation is already running. Please wait and try again.");
        }
        try {
            log.info("Admin-triggered fit-export install: profile={} packages=[{}]", profile, packages);
            StringBuilder out = new StringBuilder();
            logInfo("Admin install: fit-export profile=%s packages=[%s]".formatted(profile, packages), out);
            try {
                runScript(List.of(installFitExportScript, profile, packages), out);
            } catch (RuntimeException scriptEx) {
                logInfo("INSTALL FAILED: " + scriptEx.getMessage(), out);
                log.error("fit-export install script failed for profile={}", profile, scriptEx);
                throw new RuntimeException(out.toString(), scriptEx);
            }
            saveOrUpdate(DOMAIN1, DOMAIN2_FIT, DOMAIN3_ACTIVE_PROFILE, profile,
                    "Active fit-export profile — set by runtime install");
            saveOrUpdate(DOMAIN1, DOMAIN2_FIT, DOMAIN3_ACTIVE_PACKAGES, packages,
                    "Active fit-export pip packages — set by runtime install");
            logInfo("fit-export active profile/packages updated in DB.".formatted(), out);
            return out.toString();
        } finally {
            operationLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Status DTO
    // -------------------------------------------------------------------------

    @Data
    public static class ToolStatusDto {
        private String gcexportConfiguredVersion;
        private boolean gcexportVenvPresent;
        private String fitExportConfiguredProfile;
        private String fitExportConfiguredPackages;
        private boolean fitExportVenvPresent;
    }

    public ToolStatusDto getToolStatus() {
        ToolStatusDto dto = new ToolStatusDto();
        String gcVersion = getActiveGcexportVersion();
        dto.setGcexportConfiguredVersion(gcVersion);
        dto.setGcexportVenvPresent(Files.isDirectory(Paths.get(resolveGcexportVenvPath(gcVersion))));

        String fitProfile = getActiveFitExportProfile();
        dto.setFitExportConfiguredProfile(fitProfile);
        dto.setFitExportConfiguredPackages(getActiveFitExportPackages());
        dto.setFitExportVenvPresent(Files.isDirectory(Paths.get(resolveFitExportVenvPath(fitProfile))));
        return dto;
    }

    // -------------------------------------------------------------------------
    // Active version/profile resolution (DB → yml default fallback)
    // -------------------------------------------------------------------------

    public String getActiveGcexportVersion() {
        return readConfig(DOMAIN2_GCEXPORT, DOMAIN3_ACTIVE_VERSION, gcexportDefaultVersion);
    }

    public String getActiveFitExportProfile() {
        return readConfig(DOMAIN2_FIT, DOMAIN3_ACTIVE_PROFILE, fitExportDefaultProfile);
    }

    public String getActiveFitExportPackages() {
        return readConfig(DOMAIN2_FIT, DOMAIN3_ACTIVE_PACKAGES, fitExportDefaultPackages);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveGcexportVenvPath(String version) {
        // Script lives at /app/garmin_export/install_gcexport.sh
        // Venv lives at  /app/garmin_export/venv_gcexport_<version>/
        String dir = Paths.get(installGcexportScript).getParent().toString();
        return dir + "/venv_gcexport_" + version;
    }

    private String resolveFitExportVenvPath(String profile) {
        String dir = Paths.get(installFitExportScript).getParent().toString();
        return dir + "/venv_fit_" + profile;
    }

    private void runScript(List<String> command, StringBuilder programOutput) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logInfo(line, programOutput);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Install script exited with code %d: %s".formatted(exitCode, command));
        }
    }

    private String readConfig(String domain2, String domain3, String defaultValue) {
        List<ConfigEntity> rows = configRepository
                .findConfigEntitiesByDomain1AndDomain2AndDomain3(DOMAIN1, domain2, domain3);
        if (rows.isEmpty()) {
            return defaultValue;
        }
        String value = rows.get(0).getValue();
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    private void saveOrUpdate(String domain1, String domain2, String domain3, String value, String description) {
        List<ConfigEntity> existing = configRepository
                .findConfigEntitiesByDomain1AndDomain2AndDomain3(domain1, domain2, domain3);
        ConfigEntity entity = existing.isEmpty() ? new ConfigEntity() : existing.get(0);
        entity.setDomain1(domain1);
        entity.setDomain2(domain2);
        entity.setDomain3(domain3);
        entity.setValue(value);
        entity.setDescription(description);
        configRepository.save(entity);
    }

    private void logInfo(String msg, StringBuilder programOutput) {
        log.info(msg);
        if (programOutput != null) {
            programOutput.append("[TOOL-INSTALL] ").append(msg).append("\n");
        }
    }

    // --- Validation ---

    private void validateGcexportVersion(String version) {
        if (StringUtils.isBlank(version) || !GCEXPORT_VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "Invalid gcexport version '%s'. Expected format: v<major>.<minor>[.<patch>]  e.g. v4.6.2".formatted(version));
        }
    }

    private void validateFitProfile(String profile) {
        if (StringUtils.isBlank(profile) || !FIT_PROFILE_PATTERN.matcher(profile).matches()) {
            throw new IllegalArgumentException(
                    "Invalid fit-export profile '%s'. Only alphanumerics, hyphens and underscores are allowed.".formatted(profile));
        }
    }

    private void validatePipPackages(String packages) {
        if (StringUtils.isBlank(packages)) {
            throw new IllegalArgumentException("pip packages string must not be blank.");
        }
        for (String token : packages.split("\\s+")) {
            if (!PIP_TOKEN_PATTERN.matcher(token).matches()) {
                throw new IllegalArgumentException(
                        "Invalid pip package token '%s'. Only alphanumerics, dots, hyphens, underscores and = are allowed.".formatted(token));
            }
        }
    }
}
