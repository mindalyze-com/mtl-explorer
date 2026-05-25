package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.metadata;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@JsonPropertyOrder({
        "filterConfigRepository",
        "metadataParser"
})
public class FilterUiMetadataStartupValidator implements ApplicationRunner {

    private final FilterConfigRepository filterConfigRepository;
    private final FilterUiMetadataParser metadataParser;

    public FilterUiMetadataStartupValidator(FilterConfigRepository filterConfigRepository, FilterUiMetadataParser metadataParser) {
        this.filterConfigRepository = filterConfigRepository;
        this.metadataParser = metadataParser;
    }

    @Override
    public void run(ApplicationArguments args) {
        int validatedCount = 0;
        for (FilterConfigEntity filter : filterConfigRepository.findAll()) {
            if (metadataParser.parseLocalMetadata(filter).isPresent()) {
                validatedCount++;
            }
        }
        log.info("Validated v2 UI metadata for {} filters.", validatedCount);
    }
}
