package com.proyecto.demo.db;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseAdapter {
    Connection getConnection() throws SQLException;
    void initDatabase();
    String getDatabaseType();
    String getServerTime();
}
