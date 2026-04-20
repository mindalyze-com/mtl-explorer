package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.custom;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.FilterConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FilterConfigInitializer {

    private final FilterConfigProperties filterConfigProperties;

    private final FilterConfigRepository filterConfigRepository;

    public FilterConfigInitializer(FilterConfigProperties filterConfigProperties, FilterConfigRepository filterConfigRepository) {
        this.filterConfigProperties = filterConfigProperties;
        this.filterConfigRepository = filterConfigRepository;
    }

    @PostConstruct
    public void initializeFilterConfigs() {
        if (filterConfigProperties.getFilterConfigs() != null) {
            log.info(String.format("Did find external application.yml filter configs. " +
                                   "Create only if they do not exist yet in DB. numberOfFilters=%d", filterConfigProperties.getFilterConfigs().size()));

            filterConfigProperties.getFilterConfigs().forEach(filterConfig -> {

                if (StringUtils.isBlank(filterConfig.getFilterName()) || filterConfig.getFilterDomain() == null) {
                    String msg = "FilterConfig given by external application.yml is not valid. It must at least contain a 'filter' name and 'filterDomain'.";
                    log.error(msg);
                    throw new RuntimeException(msg);
                }

                FilterConfigEntity existingFilter = filterConfigRepository.findByFilterDomainAndFilterName(filterConfig.getFilterDomain(), filterConfig.getFilterName());
                if (existingFilter == null) {
                    log.info(String.format(("Found a filter in application.yml config which does not exist yet in the DB. " +
                                            "About to create it. filterDomain=%s, filterName=%s")
                            .formatted(filterConfig.getFilterDomain(), filterConfig.getFilterName())));
                    filterConfigRepository.save(filterConfig);

                } else {
                    log.debug(String.format("Filter does already exist. Won't update. filterDomain=%s, filterName=%s", filterConfig.getFilterDomain(), filterConfig.getFilterName()));
                }
            });
        } else {
            log.info("No external application.yml filter configs found.");
        }

    }
}
