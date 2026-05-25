package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@JsonPropertyOrder({
        "referenceResolver"
})
public class FilterTemplateGraphResolver {

    private static final Pattern TEMPLATE_INCLUDE_PATTERN = Pattern.compile("\\[\\[\\s*~\\{\\s*([^}]+?)\\s*}\\s*]]");

    private final FilterTemplateReferenceResolver referenceResolver;

    public FilterTemplateGraphResolver(FilterTemplateReferenceResolver referenceResolver) {
        this.referenceResolver = referenceResolver;
    }

    public List<FilterConfigEntity> resolveMergeOrder(FilterConfigEntity selectedFilter) {
        if (selectedFilter == null) {
            return List.of();
        }

        Map<String, FilterConfigEntity> orderedFilters = new LinkedHashMap<>();
        visit(selectedFilter, orderedFilters, new ArrayDeque<>());
        return new ArrayList<>(orderedFilters.values());
    }

    public List<String> findIncludedTemplatePaths(String expression) {
        if (StringUtils.isBlank(expression)) {
            return List.of();
        }

        List<String> templatePaths = new ArrayList<>();
        Matcher matcher = TEMPLATE_INCLUDE_PATTERN.matcher(expression);
        while (matcher.find()) {
            templatePaths.add(StringUtils.trim(matcher.group(1)));
        }
        return templatePaths;
    }

    public static String templatePathFor(FilterConfigEntity filter) {
        return "/" + filter.getFilterDomain().name() + "/" + filter.getFilterName();
    }

    private void visit(FilterConfigEntity filter, Map<String, FilterConfigEntity> orderedFilters, Deque<String> stack) {
        String currentPath = templatePathFor(filter);
        if (stack.contains(currentPath)) {
            List<String> cycle = new ArrayList<>(stack);
            cycle.add(currentPath);
            throw new IllegalStateException("Detected a cycle in filter template includes: " + String.join(" -> ", cycle));
        }
        if (orderedFilters.containsKey(currentPath)) {
            return;
        }

        stack.addLast(currentPath);
        for (String includedTemplatePath : findIncludedTemplatePaths(filter.getExpression())) {
            FilterConfigEntity includedFilter = referenceResolver.resolve(includedTemplatePath);
            visit(includedFilter, orderedFilters, stack);
        }
        stack.removeLast();

        orderedFilters.putIfAbsent(currentPath, filter);
        log.debug("Resolved filter template graph node: {}", currentPath);
    }
}
