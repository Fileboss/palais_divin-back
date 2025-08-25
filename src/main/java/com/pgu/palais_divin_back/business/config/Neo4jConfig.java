package com.pgu.palais_divin_back.business.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.config.AbstractNeo4jConfig;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EqualsAndHashCode(callSuper = true)
@Configuration
@EnableNeo4jRepositories(basePackages = "com.pgu.palais_divin_back.business.repository")
@ConfigurationProperties(prefix = "spring.neo4j")
@EnableTransactionManagement
@Data
public class Neo4jConfig extends AbstractNeo4jConfig {
    private String uri;
    private String username;
    private String password;

    @NotNull
    @Bean
    @Override
    public Driver driver() {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}