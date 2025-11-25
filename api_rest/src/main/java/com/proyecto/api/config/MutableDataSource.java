package com.proyecto.api.config;

import org.springframework.jdbc.datasource.AbstractDataSource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class MutableDataSource extends AbstractDataSource {
    private volatile DataSource target;

    public MutableDataSource() {}

    public synchronized void setTargetDataSource(DataSource ds) {
        this.target = ds;
    }

    public DataSource getTargetDataSource() {
        return this.target;
    }

    @Override
    public Connection getConnection() throws SQLException {
        DataSource t = this.target;
        if (t == null) throw new SQLException("No target DataSource set");
        return t.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        DataSource t = this.target;
        if (t == null) throw new SQLException("No target DataSource set");
        return t.getConnection(username, password);
    }
}
