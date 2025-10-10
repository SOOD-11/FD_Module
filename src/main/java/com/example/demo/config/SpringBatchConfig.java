package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import jakarta.activation.DataSource;

public class SpringBatchConfig {
	
	
	@Bean
    public DataSourceInitializer dataSourceInitializer(final javax.sql.DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        // This is the crucial line: it points directly to the SQL script inside the Spring Batch library.
        populator.addScript(new ClassPathResource("org/springframework/batch/core/schema-mysql.sql"));
        populator.setContinueOnError(false); // Fail fast if the script has an error

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);

        return initializer;
    }

}
