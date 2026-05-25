package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.metadata;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template.FilterTemplateGraphResolver;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterEffectiveUiMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
@JsonPropertyOrder({
        "objectMapper",
        "graphResolver",
        "metadataParser"
})
public class FilterMetadataResolver {

    private static final int SUPPORTED_METADATA_VERSION = FilterUiMetadataParser.SUPPORTED_METADATA_VERSION;
    private static final String FIELD_METADATA_VERSION = "metadataVersion";
    private static final String FIELD_PARAM_GROUPS = "paramGroups";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_ORIGIN_FILTER_REF = "originFilterRef";
    private static final String FIELD_RELATION = "relation";
    private static final String FIELD_OPTIONS_SOURCE = "optionsSource";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_RESOLVED_FILTER_REF = "resolvedFilterRef";
    private static final String OPTIONS_SOURCE_ORIGIN_FILTER_RESULT = "originFilterResult";
    private static final String RELATION_SELECTED = "selected";
    private static final String RELATION_INHERITED = "inherited";

    private final ObjectMapper objectMapper;
    private final FilterTemplateGraphResolver graphResolver;
    private final FilterUiMetadataParser metadataParser;

    public FilterMetadataResolver(ObjectMapper objectMapper, FilterTemplateGraphResolver graphResolver, FilterUiMetadataParser metadataParser) {
        this.objectMapper = objectMapper;
        this.graphResolver = graphResolver;
        this.metadataParser = metadataParser;
    }

    public FilterEffectiveUiMetadata resolve(FilterConfigEntity selectedFilter, Collection<String> effectiveParamNames) {
        ObjectNode mergedGroups = objectMapper.createObjectNode();
        ObjectNode mergedParams = objectMapper.createObjectNode();
        ObjectNode mergedResult = objectMapper.createObjectNode();
        Map<String, String> paramOrigins = new LinkedHashMap<>();

        for (FilterConfigEntity filter : graphResolver.resolveMergeOrder(selectedFilter)) {
            metadataParser.parseLocalMetadata(filter).ifPresent(localMetadata -> {
                ObjectNode localMetadataNode = objectMapper.valueToTree(localMetadata);

                mergeObjectMembers(mergedGroups, localMetadataNode.get(FIELD_PARAM_GROUPS), filter, FIELD_PARAM_GROUPS);
                mergeParams(mergedParams, localMetadataNode.get(FIELD_PARAMS), filter, paramOrigins);
                mergeObjectInto(mergedResult, localMetadataNode.get(FIELD_RESULT), filter, FIELD_RESULT);
            });
        }

        decorateParams(mergedParams, paramOrigins, FilterTemplateGraphResolver.templatePathFor(selectedFilter));
        validateMetadataParams(mergedParams, paramOrigins, selectedFilter, effectiveParamNames);

        return toEffectiveMetadata(mergedGroups, mergedParams, mergedResult);
    }

    private void mergeObjectMembers(ObjectNode target, JsonNode source, FilterConfigEntity filter, String fieldName) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        if (!source.isObject()) {
            log.warn("Ignoring UI metadata {} for filter {} because it is not an object", fieldName, FilterTemplateGraphResolver.templatePathFor(filter));
            return;
        }

        source.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (!value.isObject()) {
                log.warn(
                        "Ignoring UI metadata {}.{} for filter {} because it is not an object",
                        fieldName,
                        entry.getKey(),
                        FilterTemplateGraphResolver.templatePathFor(filter));
                return;
            }

            ObjectNode mergedValue = target.has(entry.getKey()) && target.get(entry.getKey()).isObject()
                    ? ((ObjectNode) target.get(entry.getKey())).deepCopy()
                    : objectMapper.createObjectNode();
            mergeObjectInto(mergedValue, value, filter, fieldName + "." + entry.getKey());
            target.set(entry.getKey(), mergedValue);
        });
    }

    private void mergeParams(ObjectNode target, JsonNode source, FilterConfigEntity filter, Map<String, String> paramOrigins) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        if (!source.isObject()) {
            log.warn("Ignoring UI metadata params for filter {} because it is not an object", FilterTemplateGraphResolver.templatePathFor(filter));
            return;
        }

        String originFilterRef = FilterTemplateGraphResolver.templatePathFor(filter);
        source.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (!value.isObject()) {
                log.warn(
                        "Ignoring UI metadata params.{} for filter {} because it is not an object",
                        entry.getKey(),
                        originFilterRef);
                return;
            }

            ObjectNode localParam = ((ObjectNode) value).deepCopy();
            localParam.remove(FIELD_ORIGIN_FILTER_REF);
            localParam.remove(FIELD_RELATION);

            ObjectNode mergedParam = target.has(entry.getKey()) && target.get(entry.getKey()).isObject()
                    ? ((ObjectNode) target.get(entry.getKey())).deepCopy()
                    : objectMapper.createObjectNode();
            mergeObjectInto(mergedParam, localParam, filter, FIELD_PARAMS + "." + entry.getKey());
            target.set(entry.getKey(), mergedParam);
            paramOrigins.put(entry.getKey(), originFilterRef);
        });
    }

    private void mergeObjectInto(ObjectNode target, JsonNode source, FilterConfigEntity filter, String fieldName) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        if (!source.isObject()) {
            log.warn("Ignoring UI metadata {} for filter {} because it is not an object", fieldName, FilterTemplateGraphResolver.templatePathFor(filter));
            return;
        }

        source.fields().forEachRemaining(entry -> {
            JsonNode childValue = entry.getValue();
            JsonNode targetValue = target.get(entry.getKey());
            if (targetValue != null && targetValue.isObject() && childValue.isObject()) {
                ObjectNode nestedTarget = ((ObjectNode) targetValue).deepCopy();
                mergeObjectInto(nestedTarget, childValue, filter, fieldName + "." + entry.getKey());
                target.set(entry.getKey(), nestedTarget);
            } else {
                target.set(entry.getKey(), childValue.deepCopy());
            }
        });
    }

    private void decorateParams(ObjectNode params, Map<String, String> paramOrigins, String selectedFilterRef) {
        params.fields().forEachRemaining(entry -> {
            String paramName = entry.getKey();
            if (!entry.getValue().isObject()) {
                return;
            }

            String originFilterRef = paramOrigins.get(paramName);
            ObjectNode param = (ObjectNode) entry.getValue();
            param.put(FIELD_ORIGIN_FILTER_REF, originFilterRef);
            param.put(FIELD_RELATION, selectedFilterRef.equals(originFilterRef) ? RELATION_SELECTED : RELATION_INHERITED);
            resolveOriginFilterResultOptions(param, originFilterRef);
        });
    }

    private void resolveOriginFilterResultOptions(ObjectNode param, String originFilterRef) {
        JsonNode optionsSource = param.get(FIELD_OPTIONS_SOURCE);
        if (optionsSource == null || !optionsSource.isObject()) {
            return;
        }

        ObjectNode optionsSourceObject = (ObjectNode) optionsSource;
        if (OPTIONS_SOURCE_ORIGIN_FILTER_RESULT.equals(optionsSourceObject.path(FIELD_TYPE).asText())) {
            optionsSourceObject.put(FIELD_RESOLVED_FILTER_REF, originFilterRef);
        }
    }

    private void validateMetadataParams(
            ObjectNode params,
            Map<String, String> paramOrigins,
            FilterConfigEntity selectedFilter,
            Collection<String> effectiveParamNames
    ) {
        Set<String> effectiveNames = new LinkedHashSet<>(effectiveParamNames == null ? Set.of() : effectiveParamNames);
        params.fieldNames().forEachRemaining(paramName -> {
            if (!effectiveNames.contains(paramName)) {
                log.warn(
                        "UI metadata param '{}' from filter {} does not exist in effective SQL params for selected filter {}",
                        paramName,
                        paramOrigins.get(paramName),
                        FilterTemplateGraphResolver.templatePathFor(selectedFilter));
            }
        });
    }

    private FilterEffectiveUiMetadata toEffectiveMetadata(ObjectNode mergedGroups, ObjectNode mergedParams, ObjectNode mergedResult) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(FIELD_METADATA_VERSION, SUPPORTED_METADATA_VERSION);
        root.set(FIELD_PARAM_GROUPS, mergedGroups);
        root.set(FIELD_PARAMS, mergedParams);
        root.set(FIELD_RESULT, mergedResult);
        return objectMapper.convertValue(root, FilterEffectiveUiMetadata.class);
    }
}
