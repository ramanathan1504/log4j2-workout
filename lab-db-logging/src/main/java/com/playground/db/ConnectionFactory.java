package com.playground.db;

import org.apache.commons.dbcp2.BasicDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionFactory {
    private static final BasicDataSource dataSource = new BasicDataSource();

    static {
        // Use localhost because the Java app is running on your machine,
        // and Docker is exposing port 3306 to localhost.
        dataSource.setUrl("jdbc:mysql://localhost:3306/WorkoutDB");
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUsername("root");
        dataSource.setPassword("root");

        // Optional: Ensure connections are valid
        dataSource.setMaxTotal(10);
        dataSource.setTestOnBorrow(true);
        dataSource.setValidationQuery("SELECT 1");
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}