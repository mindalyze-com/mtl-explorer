package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.metadata;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template.FilterTemplateGraphResolver;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@JsonPropertyOrder({
        "objectMapper",
        "strictMetadataReader"
})
public class FilterUiMetadataParser {

    public static final int SUPPORTED_METADATA_VERSION = 2;

    private static final String FIELD_METADATA_VERSION = "metadataVersion";

    private final ObjectMapper objectMapper;
    private final ObjectReader strictMetadataReader;

    public FilterUiMetadataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.strictMetadataReader = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .readerFor(FilterEffectiveUiMetadata.class);
    }

    public Optional<FilterEffectiveUiMetadata> parseLocalMetadata(FilterConfigEntity filter) {
        String rawMetadata = filter.getUiMetadata();
        if (StringUtils.isBlank(rawMetadata)) {
            return Optional.empty();
        }

        JsonNode root = readMetadataRoot(rawMetadata, filter);
        if (!root.isObject()) {
            throw invalidMetadata(filter, "UI metadata must be a JSON object");
        }

        JsonNode version = root.get(FIELD_METADATA_VERSION);
        if (version == null || version.isNull()) {
            log.warn("Ignoring non-v2 UI metadata for filter {}. metadataVersion=null", FilterTemplateGraphResolver.templatePathFor(filter));
            return Optional.empty();
        }
        if (!version.isInt()) {
            throw invalidMetadata(filter, "metadataVersion must be the integer value " + SUPPORTED_METADATA_VERSION);
        }
        if (version.asInt() != SUPPORTED_METADATA_VERSION) {
            log.warn(
                    "Ignoring non-v2 UI metadata for filter {}. metadataVersion={}",
                    FilterTemplateGraphResolver.templatePathFor(filter),
                    version);
            return Optional.empty();
        }

        FilterEffectiveUiMetadata metadata = readTypedMetadata(rawMetadata, filter);
        validateLocalMetadata(filter, metadata);
        return Optional.of(metadata);
    }

    private JsonNode readMetadataRoot(String rawMetadata, FilterConfigEntity filter) {
        try {
            return objectMapper.readTree(rawMetadata);
        } catch (JsonProcessingException ex) {
            throw invalidMetadata(filter, "UI metadata must be valid JSON", ex);
        }
    }

    private FilterEffectiveUiMetadata readTypedMetadata(String rawMetadata, FilterConfigEntity filter) {
        try {
            return strictMetadataReader.readValue(rawMetadata);
        } catch (IOException ex) {
            throw invalidMetadata(filter, "UI metadata does not match the v2 metadata contract", ex);
        }
    }

    private void validateLocalMetadata(FilterConfigEntity filter, FilterEffectiveUiMetadata metadata) {
        if (metadata.getMetadataVersion() == null || metadata.getMetadataVersion() != SUPPORTED_METADATA_VERSION) {
            throw invalidMetadata(filter, "metadataVersion must be " + SUPPORTED_METADATA_VERSION);
        }

        Map<String, FilterParamGroupMetadata> groups = metadata.getParamGroups();
        if (groups != null) {
            groups.forEach((groupKey, groupMetadata) -> validateGroupMetadata(filter, groupKey, groupMetadata));
        }

        Map<String, FilterParamMetadata> params = metadata.getParams();
        if (params != null) {
            params.forEach((paramName, paramMetadata) -> validateLocalParamMetadata(filter, paramName, paramMetadata, groups));
        }

        validateResultMetadata(filter, metadata);
    }

    private void validateGroupMetadata(FilterConfigEntity filter, String groupKey, FilterParamGroupMetadata groupMetadata) {
        if (StringUtils.isBlank(groupKey)) {
            throw invalidMetadata(filter, "paramGroups contains a blank group key");
        }
        if (groupMetadata == null) {
            throw invalidMetadata(filter, "paramGroups." + groupKey + " must be an object");
        }
        if (StringUtils.isBlank(groupMetadata.getLabel())) {
            throw invalidMetadata(filter, "paramGroups." + groupKey + ".label is required");
        }
    }

    private void validateLocalParamMetadata(
            FilterConfigEntity filter,
            String paramName,
            FilterParamMetadata paramMetadata,
            Map<String, FilterParamGroupMetadata> groups
    ) {
        if (StringUtils.isBlank(paramName)) {
            throw invalidMetadata(filter, "params contains a blank parameter key");
        }
        if (paramMetadata == null) {
            throw invalidMetadata(filter, "params." + paramName + " must be an object");
        }
        if (StringUtils.isBlank(paramMetadata.getLabel())) {
            throw invalidMetadata(filter, "params." + paramName + ".label is required");
        }
        if (StringUtils.isBlank(paramMetadata.getGroup())) {
            throw invalidMetadata(filter, "params." + paramName + ".group is required");
        }
        if (groups == null || !groups.containsKey(paramMetadata.getGroup())) {
            throw invalidMetadata(filter, "params." + paramName + ".group references unknown group '" + paramMetadata.getGroup() + "'");
        }
        if (paramMetadata.getWidget() == null) {
            throw invalidMetadata(filter, "params." + paramName + ".widget is required");
        }
        if (paramMetadata.getOriginFilterRef() != null || paramMetadata.getRelation() != null) {
            throw invalidMetadata(filter, "params." + paramName + " contains server-owned origin metadata");
        }

        FilterOptionsSourceMetadata optionsSource = paramMetadata.getOptionsSource();
        if (optionsSource != null) {
            if (optionsSource.getType() == null) {
                throw invalidMetadata(filter, "params." + paramName + ".optionsSource.type is required");
            }
            if (optionsSource.getResolvedFilterRef() != null) {
                throw invalidMetadata(filter, "params." + paramName + ".optionsSource.resolvedFilterRef is server-owned");
            }
            if (optionsSource.getType() == FilterOptionsSourceType.ORIGIN_FILTER_RESULT
                && paramMetadata.getWidget() != FilterParamWidget.TRACK_PICKER) {
                throw invalidMetadata(filter, "params." + paramName + ".optionsSource.type=originFilterResult requires widget=trackPicker");
            }
        }
    }

    private void validateResultMetadata(FilterConfigEntity filter, FilterEffectiveUiMetadata metadata) {
        if (metadata.getResult() == null || metadata.getResult().getGradient() == null) {
            return;
        }

        FilterGradientMetadata gradient = metadata.getResult().getGradient();
        if (gradient.getBucketCount() == null || gradient.getBucketCount() <= 0) {
            throw invalidMetadata(filter, "result.gradient.bucketCount must be greater than zero");
        }
        if (StringUtils.isBlank(gradient.getMetricLabel())) {
            throw invalidMetadata(filter, "result.gradient.metricLabel is required");
        }
        if (StringUtils.isBlank(gradient.getMetricUnit())) {
            throw invalidMetadata(filter, "result.gradient.metricUnit is required");
        }
        if (StringUtils.isBlank(gradient.getBucketLabel())) {
            throw invalidMetadata(filter, "result.gradient.bucketLabel is required");
        }
        if (gradient.getDirection() == null) {
            throw invalidMetadata(filter, "result.gradient.direction is required");
        }
    }

    private IllegalArgumentException invalidMetadata(FilterConfigEntity filter, String message) {
        return invalidMetadata(filter, message, null);
    }

    private IllegalArgumentException invalidMetadata(FilterConfigEntity filter, String message, Exception cause) {
        String fullMessage = "Invalid v2 UI metadata for filter %s: %s"
                .formatted(FilterTemplateGraphResolver.templatePathFor(filter), message);
        if (cause == null) {
            return new IllegalArgumentException(fullMessage);
        }
        return new IllegalArgumentException(fullMessage, cause);
    }
}
