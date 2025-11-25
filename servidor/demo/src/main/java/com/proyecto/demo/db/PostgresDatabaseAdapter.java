package com.proyecto.demo.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class PostgresDatabaseAdapter implements DatabaseAdapter {
    private final String url;
    private final String user;
    private final String password;

    public PostgresDatabaseAdapter(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public void initDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS mensajes");
            stmt.execute("DROP TABLE IF EXISTS archivos");
            stmt.execute("DROP TABLE IF EXISTS sesiones");
            stmt.execute("DROP TABLE IF EXISTS usuarios");

            stmt.execute("CREATE TABLE IF NOT EXISTS usuarios (id BIGINT PRIMARY KEY, nombre_usuario VARCHAR(100) NOT NULL UNIQUE, contrasena_hash VARCHAR(512) NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS sesiones (id CHAR(36) PRIMARY KEY, usuario_id BIGINT NOT NULL, token VARCHAR(1024), ip VARCHAR(45) NOT NULL, fecha_inicio TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), fecha_fin TIMESTAMP(6), estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA', CONSTRAINT fk_sesiones_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS archivos (id BIGSERIAL PRIMARY KEY, propietario_id BIGINT, nombre VARCHAR(1000) NOT NULL, ruta VARCHAR(2000) NOT NULL, tipo_mime VARCHAR(255), tamano BIGINT, creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT fk_archivos_propietario FOREIGN KEY (propietario_id) REFERENCES usuarios(id) ON DELETE SET NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS mensajes (id BIGSERIAL PRIMARY KEY, emisor_id BIGINT NOT NULL, receptor_id BIGINT NOT NULL, tipo VARCHAR(10) NOT NULL DEFAULT 'TEXTO' CHECK (tipo IN ('TEXTO','ARCHIVO')), contenido TEXT, archivo_id BIGINT, sesion_id CHAR(36), creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT fk_mensajes_emisor FOREIGN KEY (emisor_id) REFERENCES usuarios(id) ON DELETE CASCADE, CONSTRAINT fk_mensajes_receptor FOREIGN KEY (receptor_id) REFERENCES usuarios(id) ON DELETE CASCADE, CONSTRAINT fk_mensajes_archivo FOREIGN KEY (archivo_id) REFERENCES archivos(id) ON DELETE SET NULL, CONSTRAINT fk_mensajes_sesion FOREIGN KEY (sesion_id) REFERENCES sesiones(id) ON DELETE SET NULL)");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getDatabaseType() {
        return "Postgres";
    }

    @Override
    public String getServerTime() {
        try (var c = getConnection(); var stmt = c.createStatement(); var rs = stmt.executeQuery("SELECT NOW()")) {
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "(error)";
    }
}
