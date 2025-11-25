package com.proyecto.demo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class DataSourceConfig {
    private final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${app.db.type:auto}")
    private String preferType;

    @Value("${spring.datasource.username:root}")
    private String user;

    @Value("${spring.datasource.password:1234}")
    private String password;
    @Value("${spring.datasource.postgres.username:}")
    private String pgUser;
    @Value("${spring.datasource.postgres.password:}")
    private String pgPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        String[] mysqlCandidates = new String[] {
                "jdbc:mysql://localhost:3307/mensajeria?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC",
                "jdbc:mysql://127.0.0.1:3307/mensajeria?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC",
                "jdbc:mysql://universidad-mysql:3306/mensajeria?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
        };
        String[] pgCandidates = new String[] {
                "jdbc:postgresql://localhost:5433/mensajeria",
                "jdbc:postgresql://127.0.0.1:5433/mensajeria",
                "jdbc:postgresql://universidad-postgres:5432/mensajeria"
        };

        // order based on preferType
        String[] tryList;
        if ("postgres".equalsIgnoreCase(preferType)) {
            tryList = concat(pgCandidates, mysqlCandidates);
        } else if ("mysql".equalsIgnoreCase(preferType)) {
            tryList = concat(mysqlCandidates, pgCandidates);
        } else {
            // auto: try mysql then pg
            tryList = concat(mysqlCandidates, pgCandidates);
        }

        for (String url : tryList) {
            try {
                // select credentials based on URL type (prefer explicit Postgres props when testing pg URLs)
                String tryUser = user;
                String tryPass = password;
                if (url != null && url.startsWith("jdbc:postgresql:")) {
                    if (pgUser != null && !pgUser.isBlank()) tryUser = pgUser;
                    if (pgPassword != null && !pgPassword.isBlank()) tryPass = pgPassword;
                }

                logger.info("Attempting JDBC connect test to {} (user={})", url, tryUser);
                try (Connection c = java.sql.DriverManager.getConnection(url, tryUser, tryPass)) {
                    logger.info("Connection test succeeded to {}", url);
                }

                HikariConfig cfg = new HikariConfig();
                cfg.setJdbcUrl(url);
                cfg.setUsername(tryUser);
                cfg.setPassword(tryPass);
                cfg.setMaximumPoolSize(10);
                cfg.setPoolName("app-ds");
                HikariDataSource ds = new HikariDataSource(cfg);
                logger.info("Providing DataSource for URL {}", url);
                return ds;
            } catch (Exception e) {
                logger.info("Connect test failed for {}: {}", url, e.getMessage());
            }
        }

        throw new IllegalStateException("Unable to establish JDBC connection to any candidate DB URLs");
    }

    private String[] concat(String[] a, String[] b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
