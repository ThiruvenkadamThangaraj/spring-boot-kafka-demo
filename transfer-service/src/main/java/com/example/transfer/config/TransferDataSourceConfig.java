package com.example.transfer.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class TransferDataSourceConfig {
    
    /**
     * ITM DataSource (Source database)
     */
    @Bean(name = "itmDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.itm")
    public DataSource itmDataSource() {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .build();
    }
    
    @Bean(name = "itmJdbcTemplate")
    public JdbcTemplate itmJdbcTemplate() {
        return new JdbcTemplate(itmDataSource());
    }
    
    /**
     * CO DataSource (Target database)
     */
    @Bean(name = "coDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.co")
    public DataSource coDataSource() {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .build();
    }
    
    @Bean(name = "coJdbcTemplate")
    public JdbcTemplate coJdbcTemplate() {
        return new JdbcTemplate(coDataSource());
    }
    
    /**
     * Coordination DataSource (Partition tracking)
     */
    @Primary
    @Bean(name = "coordDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.coord")
    public DataSource coordDataSource() {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .build();
    }
    
    @Primary
    @Bean(name = "coordJdbcTemplate")
    public JdbcTemplate coordJdbcTemplate() {
        return new JdbcTemplate(coordDataSource());
    }
}
