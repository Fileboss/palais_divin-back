package com.pgu.palais_divin_back.business.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.config.AbstractNeo4jConfig;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableNeo4jRepositories(basePackages = "com.pgu.palais_divin_back.business.repository")
@EnableTransactionManagement
public class Neo4jConfig extends AbstractNeo4jConfig {

    @Bean
    public Driver driver() {
        return GraphDatabase.driver("bolt://localhost:7687",
                AuthTokens.basic("neo4j", "motdepassesecurise")); //todo .env
    }
}