package com.campuscarpool.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Central place for creating JDBC connections. Override defaults with environment variables. */
public final class DBConnection {

    private static final String URL = env("CARPOOL_DB_URL",
            "jdbc:mysql://localhost:3306/campus_carpool?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    private static final String USER = env("CARPOOL_DB_USER", "root");
    private static final String PASSWORD = env("CARPOOL_DB_PASSWORD", "root");

    private DBConnection() { }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
