package com.actiongraph.memory.spring;

import com.actiongraph.memory.InMemoryMemoryRepository;
import com.actiongraph.memory.MemoryContextLoader;
import com.actiongraph.memory.MemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "com.actiongraph.jdbc.spring.ActionGraphJdbcAutoConfiguration")
@ConditionalOnClass(MemoryRepository.class)
public class ActionGraphMemoryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public MemoryRepository actionGraphMemoryRepository() {
        return new InMemoryMemoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryContextLoader actionGraphMemoryContextLoader(MemoryRepository memoryRepository) {
        return new MemoryContextLoader(memoryRepository);
    }
}
