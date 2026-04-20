package com.x8ing.mtl.server.mtlserver.jobs.garminexport;

import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.event.InitialScanFinishedEvent;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.web.global.Utils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class GarminExporter {

    @Value("${mtl.garmin-sync.enabled}")
    private boolean enabled;

    private final AtomicBoolean gpsIndexReady = new AtomicBoolean(false);

    @Value("${mtl.garmin-sync.wrapper-program}")
    private String garminExportWrapperProgram;

    @Value("${mtl.garmin-sync.exclude_activities-file}")
    private String garminExcludeActivitiesFile;

    @Value("${mtl.garmin-sync.user-name}")
    private String garminUserName;

    // TODO: Encrypt pwd
    @Value("${mtl.garmin-sync.user-password}")
    private String garminPassword;

    @Value("${mtl.garmin-sync.fit-export-wrapper-program}")
    private String fitExportWrapperProgram;

    @Value("${mtl.garmin-sync.fit-export-save-dir}")
    private String fitExportSaveDir;

    private final SystemLogService systemLogService;

    private final GpsTrackRepository gpsTrackRepository;

    private final IndexerStatusService indexerStatusService;

    private final GarminOperationLock operationLock;

    private final GarminToolInstallService garminToolInstallService;

    private final static String SYSLOG_TOPIC2 = "GARMIN_EXPORT";

    private final static String SYSLOG_TOPIC3_MAIN_JOB = "GARMIN_EXPORT_MAIN_JOB";

    // used in case of fallback due to HTTP 408 errors (size of export too big)
    private final static String SYSLOG_TOPIC3_FALLBACK = "GARMIN_EXPORT_FALLBACK";

    // used when export is skipped due to pending GPS files or disabled
    private final static String SYSLOG_TOPIC3_SKIPPED = "GARMIN_EXPORT_SKIPPED";

    private final DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS");

    private final Utils utils;


    public GarminExporter(SystemLogService systemLogService, GpsTrackRepository gpsTrackRepository,
                          IndexerStatusService indexerStatusService,
                          Utils utils,
                          GarminOperationLock operationLock,
                          GarminToolInstallService garminToolInstallService) {
        this.systemLogService = systemLogService;
        this.gpsTrackRepository = gpsTrackRepository;
        this.indexerStatusService = indexerStatusService;
        this.utils = utils;
        this.operationLock = operationLock;
        this.garminToolInstallService = garminToolInstallService;
    }

    /**
     * Listen for the GPS index initialization event.
     * This is fired when the initial scan of GPS files is complete.
     * NOTE: We keep this event listener but don't rely on it anymore.
     * The actual check happens in checkIfShouldSkip() by querying the database.
     */
    @EventListener
    public void onInitialScanFinished(InitialScanFinishedEvent event) {
        if (!GPXDirectoryWatcherService.INDEX_GPS.equals(event.getIndex())) {
            log.debug("Received InitialScanFinishedEvent for index='{}' - ignoring (waiting for GPS)", event.getIndex());
            return;
        }
        log.info("GPS index initialScan finished (filesScanned={}, took={}ms). Garmin export can run.", event.getFilesScanned(), event.getScanDurationMs());
        gpsIndexReady.set(true);
    }

    @Data
    @AllArgsConstructor
    private static final class GarminIgnoreJson {
        List<String> ids;
    }

    public String run() throws Exception {

        // Check if export should be skipped
        String skipReason = checkIfShouldSkip();
        if (skipReason != null) {
            return skipReason;
        }

        if (operationLock.tryLock()) {
            try {
                return runExportProgramImpl();
            } finally {
                operationLock.unlock();
            }
        } else {
            RuntimeException e = new RuntimeException("Garmin export already running. Do not start over again.");
            log.info("", e);
            throw e;
        }
    }

    /**
     * The gcexport.py tool requires a file like:
     * JSON file with Array of activity IDs to exclude from download.
     * Format example: {"ids": ["6176888711"]}
     */
    @SneakyThrows
    private void writeGarminExcludeFile(StringBuilder programOutput) {
        List<String> garminActivitiesIds = gpsTrackRepository.findGarminActivitiesIds();


        GarminIgnoreJson garminIgnoreJson = new GarminIgnoreJson(garminActivitiesIds);
        String excludeJSON = utils.toJSON(garminIgnoreJson);

        log("About to write the garmin exclude file=%s with the content below: \n %s".formatted(garminExcludeActivitiesFile, excludeJSON), programOutput, true);

        Files.write(Paths.get(garminExcludeActivitiesFile), excludeJSON.getBytes());

    }

    /**
     * Seems as the "Transaction new" blocks multiple threads...
     * <p>
     * Only made public for Transaction annotation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String runExportProgramImpl() throws Exception {

        StringBuilder programOutput = new StringBuilder();
        log("START GARMIN SYNC JOB", programOutput, true);

        long t0 = System.currentTimeMillis();

        try {

            if (StringUtils.isBlank(garminPassword)) {
                throw new IllegalArgumentException("GarminPassword must be given");
            }
            if (StringUtils.isBlank(garminUserName)) {
                throw new IllegalArgumentException("GarminLogin must be given");
            }
            if (StringUtils.isBlank(garminExcludeActivitiesFile)) {
                throw new IllegalArgumentException("garminExcludeActivitiesFile must be given");
            }

            writeGarminExcludeFile(programOutput);

            // Ensure the configured gcexport version is installed (fast venv-exists check)
            String gcexportVersion = garminToolInstallService.ensureGcexportInstalled(programOutput);

            log("Start garmin export from java", programOutput, true);
            log("Supplied params: garminUserName=%s, garminPassword=%s".formatted(garminUserName, StringUtils.repeat("*", StringUtils.length(garminPassword))), programOutput, true);

            // Command to run external program — pass version so run_export.sh uses the right venv
            List<String> command = new ArrayList<>();
            command.add(garminExportWrapperProgram);
            command.add(garminUserName);
            command.add(garminPassword);
            command.add(garminExcludeActivitiesFile);
            command.add(gcexportVersion);

            // Create a ProcessBuilder instance for the command
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true); // redirect stderr to stdout

            // Start the process
            Process process = builder.start();

            // Read the output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log(line, programOutput, false);
                }
            }

            // Wait for the process to terminate
            int exitCode = process.waitFor();

            long dt = System.currentTimeMillis() - t0;

            log("Garmin Export process exited with code=%s in dtSeconds=%.2f".formatted(exitCode, 1.0 * dt / 1000), programOutput, true);

            // Check for HTTP 408 errors and retry with FIT export
            List<String> failedActivityIds = parseFailedActivityIds(programOutput.toString());
            if (!failedActivityIds.isEmpty()) {
                // Add clear visual separation
                programOutput.append("\n\n");
                log("=" + "=".repeat(80), programOutput, true);
                log("Found %d activities that failed with HTTP 408 timeout. Attempting to retry with FIT export...".formatted(failedActivityIds.size()), programOutput, true);
                log("=" + "=".repeat(80), programOutput, true);
                programOutput.append("\n");

                // Log fallback activation to database
                systemLogService.saveLog(
                        SystemLog.TOPIC1.SERVER,
                        SYSLOG_TOPIC2,
                        SYSLOG_TOPIC3_FALLBACK,
                        "HTTP 408 timeout detected - activating FIT export fallback for %d activities".formatted(failedActivityIds.size()),
                        "Activity IDs: " + String.join(", ", failedActivityIds)
                );

                // Ensure fit-export venv is present before retrying (fast check)
                String fitProfile = garminToolInstallService.ensureFitExportInstalled(programOutput);

                for (String activityId : failedActivityIds) {
                    retryFailedActivityWithFitExport(activityId, fitProfile, programOutput);
                }

                programOutput.append("\n");
                log("=" + "=".repeat(80), programOutput, true);
                log("Completed retry attempts for failed activities", programOutput, true);
                log("=" + "=".repeat(80), programOutput, true);
            }

            systemLogService.saveLog(SystemLog.TOPIC1.SERVER, SYSLOG_TOPIC2, SYSLOG_TOPIC3_MAIN_JOB, "Garmin export program completed with success", programOutput.toString());

            return programOutput.toString();

        } catch (Exception e) {
            log.error("Garmin export did fail unexpectedly. Program-Output:\n" + programOutput + "\n", e);
            systemLogService.saveLog(SystemLog.TOPIC1.SERVER, SYSLOG_TOPIC2, SYSLOG_TOPIC3_MAIN_JOB, "Garmin export program did fail", "PROGRAM_OUTPUT:\n" + programOutput + "\n" + "ERROR: \n" + e + "\n" + ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }

    private void log(String s, StringBuilder programOutput, boolean logToConsole) {

        if (logToConsole) {
            log.info(s); // no need here for a date...
        }

        String msg = LocalDateTime.now().format(df) + " " + s;
        programOutput.append(msg).append("\n");

    }

    /**
     * Parse the program output to find activity IDs that failed with HTTP 408 errors.
     * Returns a list of activity IDs that need to be retried.
     */
    private List<String> parseFailedActivityIds(String programOutput) {
        List<String> failedIds = new ArrayList<>();

        // Pattern matches: [ERROR] ... gpx/activity/20568140940?full=true or gpx/activity/20568140940
        // Captures the activity ID (digits only)
        @SuppressWarnings("RegExpRedundantEscape")
        String pattern = "\\[ERROR\\].*?gpx/activity/(\\d+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(programOutput);

        while (m.find()) {
            String activityId = m.group(1);
            if (!failedIds.contains(activityId)) {
                failedIds.add(activityId);
                log.info("Found failed activity with HTTP 408: {}", activityId);
            }
        }

        return failedIds;
    }

    /**
     * Retry a failed activity using the garmin_fit_to_gpx_export.py tool.
     * This tool downloads the FIT file and converts it to GPX.
     */
    private void retryFailedActivityWithFitExport(String activityId, String fitProfile, StringBuilder programOutput) {
        programOutput.append("\n");
        log("-".repeat(80), programOutput, true);
        log("Retrying activity %s using FIT export tool".formatted(activityId), programOutput, true);
        log("-".repeat(80), programOutput, true);

        StringBuilder fitExportOutput = new StringBuilder();

        try {
            List<String> command = new ArrayList<>();
            command.add(fitExportWrapperProgram);
            command.add(garminUserName);
            command.add(garminPassword);
            command.add(activityId);
            command.add(fitExportSaveDir);
            command.add(fitProfile); // versioned venv profile (e.g. "default")

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);

            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String logLine = "[FIT-EXPORT] " + line;
                    log(logLine, programOutput, false);
                    fitExportOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log("✅ Successfully exported activity %s using FIT export tool".formatted(activityId), programOutput, true);

                // Log success to database
                systemLogService.saveLog(
                        SystemLog.TOPIC1.SERVER,
                        SYSLOG_TOPIC2,
                        SYSLOG_TOPIC3_FALLBACK,
                        "FIT fallback SUCCESS for activity %s".formatted(activityId),
                        fitExportOutput.toString()
                );
            } else {
                log("❌ Failed to export activity %s using FIT export tool (exit code: %d)".formatted(activityId, exitCode), programOutput, true);

                // Log failure to database
                systemLogService.saveLog(
                        SystemLog.TOPIC1.SERVER,
                        SYSLOG_TOPIC2,
                        SYSLOG_TOPIC3_FALLBACK,
                        "FIT fallback FAILED for activity %s (exit code: %d)".formatted(activityId, exitCode),
                        fitExportOutput.toString()
                );
            }

        } catch (Exception e) {
            log("❌ Exception while retrying activity %s with FIT export: %s".formatted(activityId, e.getMessage()), programOutput, true);
            log.error("Error during FIT export retry for activity " + activityId, e);

            // Log exception to database
            systemLogService.saveLog(
                    SystemLog.TOPIC1.SERVER,
                    SYSLOG_TOPIC2,
                    SYSLOG_TOPIC3_FALLBACK,
                    "FIT fallback ERROR for activity %s".formatted(activityId),
                    "Error: " + e.getMessage() + "\n" + ExceptionUtils.getStackTrace(e) + "\n\nOutput:\n" + fitExportOutput.toString()
            );
        }

        // Add closing separator
        log("-".repeat(80), programOutput, true);
        programOutput.append("\n");
    }

    /**
     * Check if the Garmin export should be skipped.
     * Returns a skip reason message if export should be skipped, or null if export can proceed.
     */
    private String checkIfShouldSkip() {
        // Check if job is enabled
        if (!enabled) {
            String msg = "Garmin job not enabled. Nothing to do.";
            log.info(msg);
            systemLogService.saveLog(
                    SystemLog.TOPIC1.SERVER,
                    SYSLOG_TOPIC2,
                    SYSLOG_TOPIC3_SKIPPED,
                    "Garmin export skipped - not enabled",
                    msg
            );
            return msg;
        }

        // Check if there are pending GPS files in the database (SCHEDULED or PROCESSING)
        if (indexerStatusService.hasIndexPendingWork(GPXDirectoryWatcherService.INDEX_GPS)) {
            String msg = "GPS index has pending files (SCHEDULED or PROCESSING status). Skipping Garmin export to avoid race conditions.";
            log.info(msg);
            systemLogService.saveLog(
                    SystemLog.TOPIC1.SERVER,
                    SYSLOG_TOPIC2,
                    SYSLOG_TOPIC3_SKIPPED,
                    "Garmin export skipped - GPS files still being processed",
                    msg
            );
            return msg;
        }

        log.info("GPS index is ready (no pending files). Garmin export can proceed.");

        // No skip reason - export can proceed
        return null;
    }

}
