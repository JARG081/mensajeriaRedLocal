package com.proyecto.demo.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import com.proyecto.demo.ui.UiServerWindow;

@Component
public class DatabaseInitRunner {
    private final Logger logger = LoggerFactory.getLogger(DatabaseInitRunner.class);
    private final DatabaseSelectorService selectorService;
    @Value("${app.db.initOnStartup:false}")
    private boolean initOnStartup;

    public DatabaseInitRunner(DatabaseSelectorService selectorService) {
        this.selectorService = selectorService;
    }

    @PostConstruct
    public void onStartup() {
        var adapter = selectorService.getAdapter();
        if (adapter == null) {
            logger.error("No DatabaseAdapter available at startup");
            return;
        }
        logger.info("Using database adapter: {}", adapter.getDatabaseType());
        // Always publish DB info and a sample of table contents. Only run the destructive
        // adapter.initDatabase() if explicitly enabled via `app.db.initOnStartup=true`.
        try (Connection c = adapter.getConnection()) {
            UiServerWindow.publishMessageToUi("Using database adapter: " + adapter.getDatabaseType());
            String product = c.getMetaData().getDatabaseProductName();
            String url = c.getMetaData().getURL();
            String user = c.getMetaData().getUserName();
            String info = "Conectado a BD: " + product + " | URL=" + url + " | user=" + user;
            UiServerWindow.publishMessageToUi(info);

            // list tables and run a sample select for each
            try (ResultSet tables = c.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    UiServerWindow.publishMessageToUi("Tabla: " + tableName);
                    try (Statement st = c.createStatement()) {
                        String query = "SELECT * FROM " + tableName + " LIMIT 5";
                        try (ResultSet rs = st.executeQuery(query)) {
                            java.sql.ResultSetMetaData rmd = rs.getMetaData();
                            int cols = rmd.getColumnCount();
                            int rowCount = 0;
                            while (rs.next()) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 1; i <= cols; i++) {
                                    String colName = rmd.getColumnLabel(i);
                                    String val = rs.getString(i);
                                    sb.append(colName).append("=").append(val).append("; ");
                                }
                                UiServerWindow.publishMessageToUi("  " + sb.toString());
                                rowCount++;
                            }
                            if (rowCount == 0) {
                                UiServerWindow.publishMessageToUi("  (sin filas)");
                            }
                        }
                    } catch (Exception e) {
                        UiServerWindow.publishMessageToUi("  Error leyendo tabla " + tableName + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                UiServerWindow.publishMessageToUi("Error listando tablas: " + e.getMessage());
            }

            if (initOnStartup) {
                try {
                    adapter.initDatabase();
                    UiServerWindow.publishMessageToUi("Database init completed for " + adapter.getDatabaseType() + " (initOnStartup=true)");
                } catch (Exception e) {
                    UiServerWindow.publishMessageToUi("Error inicializando BD: " + e.getMessage());
                }
            } else {
                UiServerWindow.publishMessageToUi("app.db.initOnStartup=false -> no se ejecuta initDatabase() (previene borrado de tablas)");
            }
        } catch (Exception e) {
            logger.error("Error during DB inspection/init: {}", e.getMessage(), e);
            UiServerWindow.publishMessageToUi("No se pudo abrir conexión para inspección: " + e.getMessage());
        }
    }
}
