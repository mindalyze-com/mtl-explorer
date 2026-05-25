package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template.FilterTemplateGraphResolver;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterEffectiveUiMetadata;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterGradientDirection;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterOptionsSourceMetadata;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterOptionsSourceType;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterParamMetadata;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.metadata.FilterParamRelation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilterMetadataResolverTest {

    @Test
    void mergesV2MetadataWithOriginsAndResolvedOriginFilterOptions() {
        FilterConfigEntity base = filter(
                "SmartBaseFilter",
                """
                {
                  "metadataVersion": 2,
                  "paramGroups": {
                    "scope": { "label": "Scope", "order": 10 },
                    "tracks": { "label": "Tracks", "order": 20 }
                  },
                  "params": {
                    "DATE_TIME_FROM": {
                      "label": "From",
                      "group": "scope",
                      "widget": "dateTime",
                      "optional": true
                    },
                    "TRACK_IDS": {
                      "label": "Selected tracks",
                      "group": "tracks",
                      "widget": "trackPicker",
                      "optional": true,
                      "optionsSource": { "type": "originFilterResult" }
                    }
                  }
                }
                """);
        FilterConfigEntity child = filter(
                "TracksByDistanceGradient",
                """
                {
                  "metadataVersion": 2,
                  "paramGroups": {
                    "filter": { "label": "Filter", "order": 30, "defaultOpen": true }
                  },
                  "params": {
                    "DISTANCE_MIN_KM": {
                      "label": "Minimum distance",
                      "group": "filter",
                      "widget": "number",
                      "optional": true,
                      "unit": "km"
                    }
                  },
                  "result": {
                    "gradient": {
                      "bucketCount": 100,
                      "metricLabel": "Distance",
                      "metricUnit": "km",
                      "bucketLabel": "percentile",
                      "direction": "low-to-high"
                    }
                  }
                }
                """);
        FilterMetadataResolver resolver = resolverReturning(List.of(base, child));

        FilterEffectiveUiMetadata metadata = resolver.resolve(
                child,
                Set.of("DATE_TIME_FROM", "TRACK_IDS", "DISTANCE_MIN_KM"));

        assertThat(metadata.getParamGroups()).containsKeys("scope", "tracks", "filter");
        assertThat(param(metadata, "DATE_TIME_FROM"))
                .returns("/GPS_TRACK/SmartBaseFilter", FilterParamMetadata::getOriginFilterRef)
                .returns(FilterParamRelation.INHERITED, FilterParamMetadata::getRelation)
                .returns(true, FilterParamMetadata::getOptional);
        assertThat(param(metadata, "DISTANCE_MIN_KM"))
                .returns("/GPS_TRACK/TracksByDistanceGradient", FilterParamMetadata::getOriginFilterRef)
                .returns(FilterParamRelation.SELECTED, FilterParamMetadata::getRelation)
                .returns("km", FilterParamMetadata::getUnit);
        assertThat(optionsSource(metadata, "TRACK_IDS"))
                .returns(FilterOptionsSourceType.ORIGIN_FILTER_RESULT, FilterOptionsSourceMetadata::getType)
                .returns("/GPS_TRACK/SmartBaseFilter", FilterOptionsSourceMetadata::getResolvedFilterRef);
        assertThat(metadata.getResult().getGradient())
                .returns("Distance", gradient -> gradient.getMetricLabel())
                .returns(FilterGradientDirection.LOW_TO_HIGH, gradient -> gradient.getDirection());
    }

    @Test
    void marksBaseFilterOwnParamsAsSelectedWhenBaseFilterIsSelected() {
        FilterConfigEntity base = filter(
                "SmartBaseFilter",
                """
                {
                  "paramGroups": {
                    "tracks": { "label": "Tracks", "order": 20 }
                  },
                  "metadataVersion": 2,
                  "params": {
                    "TRACK_IDS": {
                      "label": "Selected tracks",
                      "group": "tracks",
                      "widget": "trackPicker",
                      "optionsSource": { "type": "originFilterResult" }
                    }
                  }
                }
                """);
        FilterMetadataResolver resolver = resolverReturning(List.of(base));

        FilterEffectiveUiMetadata metadata = resolver.resolve(base, Set.of("TRACK_IDS"));

        assertThat(param(metadata, "TRACK_IDS"))
                .returns("/GPS_TRACK/SmartBaseFilter", FilterParamMetadata::getOriginFilterRef)
                .returns(FilterParamRelation.SELECTED, FilterParamMetadata::getRelation);
        assertThat(optionsSource(metadata, "TRACK_IDS"))
                .returns("/GPS_TRACK/SmartBaseFilter", FilterOptionsSourceMetadata::getResolvedFilterRef);
    }

    @Test
    void ignoresNonV2MetadataInsteadOfParsingLegacyRootKeys() {
        FilterConfigEntity legacy = filter(
                "ActivitiesByKeyword",
                """
                {
                  "optionalParams": ["SEARCH_WORD"],
                  "params": {
                    "SEARCH_WORD": {
                      "label": "Keyword",
                      "optional": true
                    }
                  }
                }
                """);
        FilterMetadataResolver resolver = resolverReturning(List.of(legacy));

        FilterEffectiveUiMetadata metadata = resolver.resolve(legacy, Set.of("SEARCH_WORD"));

        assertThat(metadata.getParams()).isEmpty();
    }

    @Test
    void rejectsMalformedV2Metadata() {
        FilterConfigEntity bad = filter(
                "ActivitiesByKeyword",
                """
                {
                  "metadataVersion": 2,
                  "optionalParams": ["SEARCH_WORD"],
                  "params": {
                    "SEARCH_WORD": {
                      "label": "Keyword",
                      "group": "filter",
                      "widget": "text"
                    }
                  }
                }
                """);
        FilterMetadataResolver resolver = resolverReturning(List.of(bad));

        assertThatThrownBy(() -> resolver.resolve(bad, Set.of("SEARCH_WORD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match the v2 metadata contract");
    }

    @Test
    void rejectsServerOwnedFieldsInLocalMetadata() {
        FilterConfigEntity bad = filter(
                "SmartBaseFilter",
                """
                {
                  "metadataVersion": 2,
                  "paramGroups": {
                    "tracks": { "label": "Tracks", "order": 20 }
                  },
                  "params": {
                    "TRACK_IDS": {
                      "originFilterRef": "/GPS_TRACK/SmartBaseFilter",
                      "label": "Selected tracks",
                      "group": "tracks",
                      "widget": "trackPicker"
                    }
                  }
                }
                """);
        FilterMetadataResolver resolver = resolverReturning(List.of(bad));

        assertThatThrownBy(() -> resolver.resolve(bad, Set.of("TRACK_IDS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server-owned origin metadata");
    }

    private static FilterParamMetadata param(FilterEffectiveUiMetadata metadata, String paramName) {
        return metadata.getParams().get(paramName);
    }

    private static FilterOptionsSourceMetadata optionsSource(FilterEffectiveUiMetadata metadata, String paramName) {
        return param(metadata, paramName).getOptionsSource();
    }

    private static FilterMetadataResolver resolverReturning(List<FilterConfigEntity> mergeOrder) {
        FilterTemplateGraphResolver graphResolver = mock(FilterTemplateGraphResolver.class);
        FilterConfigEntity selectedFilter = mergeOrder.getLast();
        when(graphResolver.resolveMergeOrder(selectedFilter)).thenReturn(mergeOrder);
        ObjectMapper objectMapper = new ObjectMapper();
        return new FilterMetadataResolver(objectMapper, graphResolver, new FilterUiMetadataParser(objectMapper));
    }

    private static FilterConfigEntity filter(String name, String uiMetadata) {
        FilterConfigEntity filter = new FilterConfigEntity();
        filter.setFilterDomain(FilterConfigEntity.FILTER_DOMAIN.GPS_TRACK);
        filter.setFilterName(name);
        filter.setUiMetadata(uiMetadata);
        return filter;
    }
}
