package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CustomTemplateResolver extends StringTemplateResolver {

    private final FilterConfigRepository filterConfigRepository;

    public CustomTemplateResolver(FilterConfigRepository filterConfigRepository) {
        this.filterConfigRepository = filterConfigRepository;
        setTemplateMode(TemplateMode.TEXT); // make sure Thymeleaf won't replace special chars with HTML entities...
        setCacheable(true); // Enable caching
        setCacheTTLMs(1000L); // Set cache TTL to a short time, still it avoids internal repetitions
    }

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, Map<String, Object> templateResolutionAttributes) {

        String templatePathRegex = "^/([^/]+)/([^/]+)$"; // eg. /GPS_TRACK/StandardFilter

        // Compile the regex pattern
        Pattern pattern = Pattern.compile(templatePathRegex);

        // Create a matcher to match the pattern in the templatePath
        Matcher matcher = pattern.matcher(template);

        // Check if the pattern matches
        if (matcher.matches()) {
            // Extract the components using group() method
            String filterDomain = matcher.group(1);
            String filterName = matcher.group(2);

            if (StringUtils.isBlank(filterDomain) || StringUtils.isBlank(filterName)) {
                failOnTemplateNameSyntax(template);
            }

            log.debug("About to resolve template: %s".formatted(template));

            FilterConfigEntity filter = filterConfigRepository.findByFilterDomainAndFilterName(
                    FilterConfigEntity.FILTER_DOMAIN.valueOf(filterDomain),
                    filterName);

            if (filter == null) {
                failWithFilterNotFoundInDB(filterDomain, filterName);
            } else {
                // all went well, return the template content
                return new StringTemplateResource(filter.getExpression());
            }

        } else {
            failOnTemplateNameSyntax(template);
        }

        failOnTemplateNameSyntax(template);
        return null; // won't reach, as it will throw a runtime... can this be done nicer?

    }

    private static void failWithFilterNotFoundInDB(String filterDomain, String filterName) {
        String msg = "Did not find the given filter template in the database. Make sure it exists with a domain='%s' and name='%s'.".formatted(filterDomain, filterName);
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
