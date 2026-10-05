package com.starstack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs schema.sql idempotently before JPA validates the schema.
 * Spring Boot's auto-configured {@code spring.sql.init.mode=always} doesn't
 * always fire in time when JPA validation runs. Run manually here.
 */
@Component
@Order(0)
public class SchemaInitializer implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInitializer.class);

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        LOG.info("Running schema.sql against DB...");
        ScriptUtils.executeSqlScript(dataSource.getConnection(),
                new ClassPathResource("schema.sql"));
        LOG.info("schema.sql done");
    }
}