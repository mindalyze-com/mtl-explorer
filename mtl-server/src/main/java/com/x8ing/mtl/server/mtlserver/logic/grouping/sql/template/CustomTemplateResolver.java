package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import java.util.Map;

@Component
@Slf4j
@JsonPropertyOrder({
        "referenceResolver"
})
public class CustomTemplateResolver extends StringTemplateResolver {

    private final FilterTemplateReferenceResolver referenceResolver;

    public CustomTemplateResolver(FilterTemplateReferenceResolver referenceResolver) {
        this.referenceResolver = referenceResolver;
        setTemplateMode(TemplateMode.TEXT); // make sure Thymeleaf won't replace special chars with HTML entities...
        setCacheable(true); // Enable caching
        setCacheTTLMs(1000L); // Set cache TTL to a short time, still it avoids internal repetitions
    }

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, Map<String, Object> templateResolutionAttributes) {

        log.debug("About to resolve template: {}", template);
        FilterConfigEntity filter = referenceResolver.resolve(template);
        return new StringTemplateResource(filter.getExpression());
    }
}
