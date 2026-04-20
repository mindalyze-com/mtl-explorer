package com.x8ing.mtl.server.mtlserver.web.services.config;

import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigRepository configRepository;

    public ConfigController(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @RequestMapping("/get")
    public List<ConfigEntity> get(@RequestParam(required = false) String domain1, @RequestParam(required = false) String domain2, @RequestParam(required = false) String domain3) {

        if (StringUtils.isBlank(domain1) && StringUtils.isBlank(domain2) && StringUtils.isBlank(domain3)) {
            return configRepository.findAll();
        }

        if (StringUtils.isNotBlank(domain1) && StringUtils.isNotBlank(domain2) && StringUtils.isNotBlank(domain3)) {
            return configRepository.findConfigEntitiesByDomain1AndDomain2AndDomain3(domain1, domain2, domain3);
        }

        if (StringUtils.isNotBlank(domain1) && StringUtils.isNotBlank(domain2)) {
            return configRepository.findConfigEntitiesByDomain1AndDomain2(domain1, domain2);
        }

        if (StringUtils.isNotBlank(domain1)) {
            return configRepository.findConfigEntitiesByDomain1(domain1);
        }

        throw new IllegalArgumentException("Illegal combination of arguments. Either don't pass any domain at all, or search for domain 1, or domains 1,2 or for domains 1,2,3");
    }

}
