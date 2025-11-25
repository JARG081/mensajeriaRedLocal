package com.proyecto.webmvc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

@Component
public class DbDiscoveryRunner implements CommandLineRunner {
    private final Logger logger = LoggerFactory.getLogger(DbDiscoveryRunner.class);

    @Value("${server.external.baseUrl:http://localhost:9010}")
    private String serverBase;

    @Autowired(required = false)
    private ConfigurableApplicationContext ctx;

    @Autowired(required = false)
    private JdbcTemplate jdbc;

    @Value("${spring.datasource.postgres.username:postgres}")
    private String pgUser;
    @Value("${spring.datasource.postgres.password:1234}")
    private String pgPass;
    @Value("${spring.datasource.username:root}")
    private String mysqlUser;
    @Value("${spring.datasource.password:1234}")
    private String mysqlPass;

    @Override
    public void run(String... args) throws Exception {
        try {
            var rt = new RestTemplate();
            var url = serverBase + "/api/dbinfo";
            Map<String,Object> res = null;
            int tries = 0;
            while (tries < 6) {
                try {
                    res = rt.getForObject(url, Map.class);
                    if (res != null) break;
                } catch (Exception ignored) {}
                tries++;
                logger.debug("DbDiscoveryRunner: attempt {} failed to contact {} - retrying in 1500ms", tries, url);
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            }
            logger.info("Discovered DB info from server {}: {}", url, res);
            if (res != null && res.containsKey("url")) {
                String serverUrl = res.get("url") == null ? null : res.get("url").toString();
                String serverUser = res.get("user") == null ? null : res.get("user").toString();
                // sanitize username like 'root@172.18.0.1' -> 'root'
                if (serverUser != null && serverUser.contains("@")) serverUser = serverUser.split("@",2)[0];
                String serverPass = res.get("password") == null ? null : res.get("password").toString();
                if (serverUrl != null && jdbc != null) {
                    try {
                        DataSource current = jdbc.getDataSource();
                        String currentUrl = null;
                        if (current != null) {
                            try {
                                // If jdbc is backed by MutableDataSource, check its target first
                                if (current instanceof MutableDataSource) {
                                    DataSource target = ((MutableDataSource) current).getTargetDataSource();
                                    if (target != null) {
                                        try (var c = target.getConnection()) { currentUrl = c.getMetaData().getURL(); }
                                    }
                                } else {
                                    try (var c = current.getConnection()) { currentUrl = c.getMetaData().getURL(); }
                                }
                            } catch (Exception ignored) {
                                // if we can't read current URL, leave it null so reconfiguration proceeds
                            }
                        }
                        if (!serverUrl.equals(currentUrl)) {
                            logger.info("Server indicates different DB URL (server='{}' local='{}') - reconfiguring DataSource", serverUrl, currentUrl);
                            String tryUser = serverUser;
                            String tryPass = serverPass;
                            if (tryPass == null || tryPass.isBlank()) {
                                if (serverUrl.startsWith("jdbc:postgresql:")) {
                                    if (tryUser == null || tryUser.isBlank()) tryUser = pgUser;
                                    tryPass = pgPass;
                                } else {
                                    if (tryUser == null || tryUser.isBlank()) tryUser = mysqlUser;
                                    tryPass = mysqlPass;
                                }
                            }
                            HikariConfig cfg = new HikariConfig();
                            cfg.setJdbcUrl(serverUrl);
                            cfg.setUsername(tryUser);
                            cfg.setPassword(tryPass);
                            String desiredDriver = null;
                            if (serverUrl.startsWith("jdbc:postgresql:")) desiredDriver = "org.postgresql.Driver";
                            else if (serverUrl.startsWith("jdbc:mysql:")) desiredDriver = "com.mysql.cj.jdbc.Driver";
                            if (desiredDriver != null) {
                                try {
                                    Class.forName(desiredDriver);
                                    cfg.setDriverClassName(desiredDriver);
                                } catch (ClassNotFoundException cnf) {
                                    logger.warn("JDBC driver class {} not found on classpath, will try without driverClassName: {}", desiredDriver, cnf.getMessage());
                                }
                            }
                            logger.info("Configuring HikariDataSource for URL {} with user='{}'", serverUrl, tryUser == null ? "(null)" : tryUser);
                            var ds = createHikariDataSourceWithFallback(cfg, serverUrl);
                            if (ctx != null) {
                                try {
                                    var bf = (DefaultListableBeanFactory) ctx.getBeanFactory();
                                    // If a MutableDataSource is present, set its target; otherwise replace singleton
                                    try {
                                        if (ctx.containsBean("dataSource")) {
                                            Object dsBean = ctx.getBean("dataSource");
                                            if (dsBean instanceof MutableDataSource) {
                                                        MutableDataSource mds = (MutableDataSource) dsBean;
                                                DataSource prev = mds.getTargetDataSource();
                                                if (prev instanceof HikariDataSource) {
                                                    try { ((HikariDataSource) prev).close(); } catch (Exception ignored) {}
                                                }
                                                        mds.setTargetDataSource((DataSource) ds);
                                                logger.info("Updated MutableDataSource target with server-provided DataSource");
                                            } else {
                                                try { bf.destroySingleton("dataSource"); } catch (Exception ignored) {}
                                                bf.registerSingleton("dataSource", ds);
                                                logger.info("Replaced dataSource singleton with server-provided DB URL");
                                            }
                                        } else {
                                            bf.registerSingleton("dataSource", ds);
                                            logger.info("Registered dataSource singleton with server-provided DB URL");
                                        }
                                    } catch (Exception ignored) {}
                                } catch (Exception be) {
                                    logger.warn("Could not replace singleton dataSource: {}", be.getMessage());
                                }
                            }
                            try {
                                // set jdbc template datasource as well
                                jdbc.setDataSource((DataSource) ds);
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to reconfigure DataSource from server dbinfo: {}", ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not discover DB info from server {}: {}", serverBase, e.getMessage());
        }
    }

    private HikariDataSource createHikariDataSourceWithFallback(HikariConfig cfg, String serverUrl) throws Exception {
        try {
            HikariDataSource ds = new HikariDataSource(cfg);
            try (var c = ds.getConnection()) { /* ok */ }
            return ds;
        } catch (Exception firstEx) {
            logger.warn("Initial HikariDataSource creation failed for {}: {}", serverUrl, firstEx.getMessage());
            try {
                cfg.setDriverClassName(null);
                HikariDataSource ds2 = new HikariDataSource(cfg);
                try (var c = ds2.getConnection()) { /* ok */ }
                logger.info("HikariDataSource created for {} using fallback without explicit driverClassName", serverUrl);
                return ds2;
            } catch (Exception secondEx) {
                logger.error("Fallback HikariDataSource creation also failed for {}: {}", serverUrl, secondEx.getMessage());
                throw secondEx;
            }
        }
    }

}
