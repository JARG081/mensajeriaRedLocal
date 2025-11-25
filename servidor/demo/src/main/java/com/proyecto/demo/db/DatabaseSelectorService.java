package com.proyecto.demo.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;

@Service
public class DatabaseSelectorService {
    private final Logger logger = LoggerFactory.getLogger(DatabaseSelectorService.class);

    @Value("${app.db.type:auto}")
    private String preferType; // mysql, postgres, auto

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

    private DatabaseAdapter adapter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private javax.sql.DataSource dataSource;

    @PostConstruct
    public void init() {
        logger.info("DB selector preferType='{}' configuredUrl='{}'", preferType, configuredUrl);
        if ("postgres".equalsIgnoreCase(preferType)) {
            tryUsePostgres();
        } else if ("mysql".equalsIgnoreCase(preferType)) {
            tryUseMySql();
        } else { // auto
            // try mysql localhost:3307 first then postgres, or viceversa
            boolean ok = tryUseMySql();
            if (!ok) ok = tryUsePostgres();
            if (!ok) {
                throw new IllegalStateException("Unable to connect to any DB (auto)");
            }
        }
    }

    private boolean tryUseMySql() {
        String url = configuredUrl != null && configuredUrl.contains("3307") ? configuredUrl : "jdbc:mysql://localhost:3307/mensajeria?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
        try {
            var a = new MySQLDatabaseAdapter(url, user, password);
            try (Connection c = a.getConnection()) {
                logger.info("Connected to MySQL via {}", url);
            }
            adapter = a;
            return true;
        } catch (Exception e) {
            logger.info("MySQL connection failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryUsePostgres() {
        // Prefer explicit Postgres properties if present; otherwise fall back to configuredUrl or docker mapping
        String url;
        if (pgUrl != null && !pgUrl.isBlank()) {
            url = pgUrl;
        } else if (configuredUrl != null && configuredUrl.contains("5433")) {
            url = configuredUrl;
        } else {
            url = "jdbc:postgresql://localhost:5433/mensajeria";
        }
        // user/password preference: use explicit pgUser/pgPassword if present; otherwise fallbacks
        String tryUser = (pgUser != null && !pgUser.isBlank()) ? pgUser : ((user == null || "root".equalsIgnoreCase(user)) ? "postgres" : user);
        String tryPass = (pgPassword != null && !pgPassword.isBlank()) ? pgPassword : ((password == null || password.isBlank()) ? "1234" : password);
        try {
            var a = new PostgresDatabaseAdapter(url, tryUser, tryPass);
            try (Connection c = a.getConnection()) {
                logger.info("Connected to Postgres via {} (user={})", url, tryUser);
            }
            adapter = new PostgresDatabaseAdapter(url, tryUser, tryPass);
            return true;
        } catch (Exception e) {
            logger.info("Postgres connection failed (url={}, user={}): {}", url, tryUser, e.getMessage());
            return false;
        }
    }

    public DatabaseAdapter getAdapter() {
        // if adapter not yet set but dataSource available, derive adapter from datasource metadata
        if (adapter == null && dataSource != null) {
            try {
                try (var c = dataSource.getConnection()) {
                    String product = c.getMetaData().getDatabaseProductName();
                    String url = c.getMetaData().getURL();
                    logger.info("DataSource reports product='{}' url='{}'", product, url);
                    if (product != null && product.toLowerCase().contains("postgres")) {
                        String u = (pgUser != null && !pgUser.isBlank()) ? pgUser : user;
                        String p = (pgPassword != null && !pgPassword.isBlank()) ? pgPassword : password;
                        adapter = new com.proyecto.demo.db.PostgresDatabaseAdapter(url, u, p);
                    } else {
                        adapter = new com.proyecto.demo.db.MySQLDatabaseAdapter(url, user, password);
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not derive adapter from DataSource: {}", e.getMessage());
            }
        }
        return adapter;
    }
}
