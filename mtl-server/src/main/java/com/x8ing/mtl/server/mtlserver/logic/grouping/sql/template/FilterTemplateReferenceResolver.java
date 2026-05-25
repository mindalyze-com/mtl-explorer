package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@JsonPropertyOrder({
        "filterConfigRepository"
})
public class FilterTemplateReferenceResolver {

    private static final Pattern TEMPLATE_PATH_PATTERN = Pattern.compile("^/([^/]+)/([^/]+)$");

    private final FilterConfigRepository filterConfigRepository;

    public FilterTemplateReferenceResolver(FilterConfigRepository filterConfigRepository) {
        this.filterConfigRepository = filterConfigRepository;
    }

    public FilterTemplateReference parse(String templatePath) {
        Matcher matcher = TEMPLATE_PATH_PATTERN.matcher(StringUtils.trimToEmpty(templatePath));
        if (!matcher.matches()) {
            failOnTemplateNameSyntax(templatePath);
        }

        String filterDomainName = matcher.group(1);
        String filterName = matcher.group(2);
        if (StringUtils.isBlank(filterDomainName) || StringUtils.isBlank(filterName)) {
            failOnTemplateNameSyntax(templatePath);
        }

        FilterConfigEntity.FILTER_DOMAIN filterDomain;
        try {
            filterDomain = FilterConfigEntity.FILTER_DOMAIN.valueOf(filterDomainName);
        } catch (IllegalArgumentException ex) {
            String msg = "The filter template path has an unknown domain '%s'. Got template path: %s"
                    .formatted(filterDomainName, templatePath);
            log.error(msg);
            throw new IllegalArgumentException(msg, ex);
        }

        return new FilterTemplateReference(filterDomain, filterName);
    }

    public FilterConfigEntity resolve(String templatePath) {
        return resolve(parse(templatePath));
    }

    public FilterConfigEntity resolve(FilterTemplateReference reference) {
        FilterConfigEntity filter = filterConfigRepository.findByFilterDomainAndFilterName(
                reference.filterDomain(),
                reference.filterName());

        if (filter == null) {
            failWithFilterNotFoundInDB(reference.filterDomain().name(), reference.filterName());
        }

        return filter;
    }

    private static void failWithFilterNotFoundInDB(String filterDomain, String filterName) {
        String msg = "Did not find the given filter template in the database. Make sure it exists with a domain='%s' and name='%s'."
                .formatted(filterDomain, filterName);
        log.error(msg);
        throw new IllegalArgumentException(msg);
    }

    private static void failOnTemplateNameSyntax(String template) {
        String msg = "The filter name did not follow the template path syntax, " +
                     "it must follow the format /$filter_domain/$filter_name. " +
                     "Example: /GPS_TRACK/StandardFilter or /GPS_TRACK/MyHikingFilter." +
                     "Got the name: %s".formatted(template);
        log.error(msg);
        throw new IllegalArgumentException(msg);
    }
}
