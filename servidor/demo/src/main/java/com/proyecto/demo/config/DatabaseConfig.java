package com.proyecto.demo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import javax.sql.DataSource;

@Configuration
@ConditionalOnMissingBean(javax.sql.DataSource.class)
public class DatabaseConfig {
    private final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${app.db.type:auto}")
    private String preferType;

    @Value("${spring.datasource.url:}")
    private String configuredUrl;

    @Value("${spring.datasource.username:root}")
    private String user;

    @Value("${spring.datasource.password:1234}")
    private String password;

    @Value("${spring.datasource.postgres.url:}")
    private String pgUrl;

    @Value("${spring.datasource.postgres.username:}")
    private String pgUser;

    @Value("${spring.datasource.postgres.password:}")
    private String pgPassword;

    @Bean
    public DataSource selectedDataSource() {
        String url = null;
        String username = null;
        String pwd = null;

        if ("postgres".equalsIgnoreCase(preferType)) {
            if (pgUrl != null && !pgUrl.isBlank()) url = pgUrl;
            else if (configuredUrl != null && configuredUrl.contains("5433")) url = configuredUrl;
            else url = "jdbc:postgresql://localhost:5433/mensajeria";

            username = (pgUser != null && !pgUser.isBlank()) ? pgUser : ((user == null || "root".equalsIgnoreCase(user)) ? "postgres" : user);
            pwd = (pgPassword != null && !pgPassword.isBlank()) ? pgPassword : ((password == null || password.isBlank()) ? "1234" : password);
        } else if ("mysql".equalsIgnoreCase(preferType)) {
            if (configuredUrl != null && !configuredUrl.isBlank()) url = configuredUrl;
            else url = "jdbc:mysql://localhost:3307/mensajeria?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
            username = (user == null || user.isBlank()) ? "root" : user;
            pwd = (password == null || password.isBlank()) ? "1234" : password;
        } else {
            if (configuredUrl != null && configuredUrl.contains("5433")) {
                url = configuredUrl;
                username = (pgUser != null && !pgUser.isBlank()) ? pgUser : ((user == null || "root".equalsIgnoreCase(user)) ? "postgres" : user);
                pwd = (pgPassword != null && !pgPassword.isBlank()) ? pgPassword : ((password == null || password.isBlank()) ? "1234" : password);
            } else if (configuredUrl != null && configuredUrl.contains("3307")) {
                url = configuredUrl;
                username = (user == null || user.isBlank()) ? "root" : user;
                pwd = (password == null || password.isBlank()) ? "1234" : password;
            } else {
                url = "jdbc:mysql://localhost:3307/mensajeria?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
                username = (user == null || user.isBlank()) ? "root" : user;
                pwd = (password == null || password.isBlank()) ? "1234" : password;
            }
        }

        logger.info("Creating primary DataSource: url='{}' user='{}' (app.db.type={})", url, username, preferType);

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(pwd);
        // driver
        if (url != null && url.startsWith("jdbc:postgresql:")) cfg.setDriverClassName("org.postgresql.Driver");
        else if (url != null && url.startsWith("jdbc:mysql:")) cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");

        cfg.setMaximumPoolSize(6);
        cfg.setPoolName("app-hikari-pool");

        return new HikariDataSource(cfg);
    }
}
