package com.codepath.backend.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {
    @Bean(destroyMethod = "close")
    Driver neo4jDriver(@Value("${codepath.neo4j.uri}") String uri,
                       @Value("${codepath.neo4j.username}") String username,
                       @Value("${codepath.neo4j.password}") String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}
