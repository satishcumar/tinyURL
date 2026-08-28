package com.tinyurl.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class TestProfileIntegrationTest {

    private final DataSource dataSource;
    private final Environment environment;

    @Autowired
    TestProfileIntegrationTest(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Test
    void testProfileUsesInMemoryDatabaseAndDisablesH2Console() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:tinyurl-test"));
        }
        assertEquals("false", environment.getProperty("spring.h2.console.enabled"));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("true", environment.getProperty("spring.flyway.enabled"));

        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) >= 2);
        }

        try (Connection connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            try (var columns = metadata.getColumns(null, null, "URL_MAPPING", "EXPIRES_AT")) {
                assertTrue(columns.next());
            }
            try (var tables = metadata.getTables(null, null, "REDIRECT_EVENT", new String[]{"TABLE"})) {
                assertTrue(tables.next());
            }
        }
    }
}
