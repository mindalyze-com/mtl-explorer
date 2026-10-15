package com.x8ing.mtl.server.mtlserver.testsupport;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/**
 * Integration fixtures must not race background jobs in their explicitly selected test database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class NoScheduledJobs {
    @Bean
    static BeanFactoryPostProcessor disableScheduling() {
        return factory -> ((BeanDefinitionRegistry) factory)
                .removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
    }
}
