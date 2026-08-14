package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * select id from gps_track gt where start_date BETWEEN TO_TIMESTAMP(:DATE_FROM, 'YYYY-MM-DD HH24:MI:SS') AND TO_TIMESTAMP(:DATE_TO, 'YYYY-MM-DD HH24:MI:SS') order by start_date
 * INTERSECT
 * [[@{/GPS_TRACK/StandardFilter}]]
 * INTERSECT
 * [# th:include="/GPS_TRACK/StandardFilter"][/]
 */

@Service
@JsonPropertyOrder({
        "referenceResolver"
})
public class TemplateProcessingService {

    private static final Pattern TEMPLATE_INCLUDE_PATTERN = Pattern.compile("\\[\\[\\s*~\\{\\s*([^}]+?)\\s*}\\s*]]");

    private final FilterTemplateReferenceResolver referenceResolver;

    public TemplateProcessingService(FilterTemplateReferenceResolver referenceResolver) {
        this.referenceResolver = referenceResolver;
    }

    public String processTemplate(String templatePath) {
        return processTemplate(templatePath, new ArrayDeque<>());
    }

    private String processTemplate(String templatePath, Deque<String> stack) {
        String currentPath = StringUtils.trimToEmpty(templatePath);
        FilterTemplateTraversal.enter(stack, currentPath);
        FilterConfigEntity filter = referenceResolver.resolve(currentPath);
        String resolved = expandIncludes(filter.getExpression(), stack);
        FilterTemplateTraversal.leave(stack);
        return resolved;
    }

    private String expandIncludes(String expression, Deque<String> stack) {
        if (StringUtils.isBlank(expression)) {
            return expression;
        }

        Matcher matcher = TEMPLATE_INCLUDE_PATTERN.matcher(expression);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String includedTemplatePath = StringUtils.trim(matcher.group(1));
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(processTemplate(includedTemplatePath, stack)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

}
