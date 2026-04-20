package com.x8ing.mtl.server.mtlserver.jobs.sqlformat;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * SQL's inserted with liquibase and CDATA have an ugly indention... this tries to fix that.
 */
@Slf4j
@Service
public class LiquibaseIndentFixerJob {

    private final FilterConfigRepository filterConfigRepository;

    public LiquibaseIndentFixerJob(FilterConfigRepository filterConfigRepository) {
        this.filterConfigRepository = filterConfigRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run() {

        long t0 = System.currentTimeMillis();
        int patchedEntries = 0;

        // Fetch all filter configs with a single audit entry
        List<FilterConfigEntity> filterConfigs = filterConfigRepository.findAllWithSingleAuditEntry();

        for (FilterConfigEntity filterConfig : filterConfigs) {

            if (FilterConfigEntity.FILTER_TYPE.SQL.equals(filterConfig.filterType) && StringUtils.isNotBlank(filterConfig.getExpression())) {
                patchedEntries++;

                String sql = removeMinimalIndent(filterConfig.getExpression());
                filterConfig.setExpression(sql);
                filterConfig.setUpdateDate(new Date()); // force an update, even if the SQL is actually unchanged (seems to be smartly detected by hibernate), however we don't want to reprocess

                filterConfigRepository.save(filterConfig);
            }
        }

        if (patchedEntries > 0) {
            log.info("Completed liquibase Indent Fixer Job in dt[ms]={} for {} entries", System.currentTimeMillis() - t0, patchedEntries);
        }
    }

    public static String removeMinimalIndent(String sql) {

        if (StringUtils.isEmpty(sql)) {
            return sql;
        }

        // Split the SQL into individual lines
        String[] lines = sql.split("\n");

        // Start searching for minimal indent from the second line
        int minIndent = getMinIndent(lines);

        // Create a new StringBuilder to store the formatted SQL
        StringBuilder result = new StringBuilder();

        // Keep the first line as is
        result.append(lines[0]).append("\n");

        // Remove the minimal indent from subsequent lines
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                result.append("\n");
            } else {
                result.append(lines[i].substring(minIndent)).append("\n");
            }
        }

        return result.toString().trim();  // Trim any extra trailing newlines
    }

    private static int getMinIndent(String[] lines) {
        int minIndent = Integer.MAX_VALUE;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];

            // Ignore empty lines
            if (line.trim().isEmpty()) {
                continue;
            }

            // Count leading spaces (you can adjust this to count tabs if needed)
            int leadingSpaces = countLeadingSpaces(line);

            // Track the minimal indent level
            if (leadingSpaces < minIndent) {
                minIndent = leadingSpaces;
            }
        }
        return minIndent;
    }

    // Helper function to count leading spaces or tabs
    private static int countLeadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                count++;
            } else if (c == '\t') {
                count += 4;  // tab replaced with spaces
            } else {
                break;
            }
        }
        return count;
    }


}
