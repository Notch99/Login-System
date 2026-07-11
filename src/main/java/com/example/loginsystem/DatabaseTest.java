package com.example.loginsystem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

public class DatabaseTest {
    public static void main(String[] args) {
        Properties config = new Properties();
        String configFile = "config/loginsystem.properties";
        
        try {
            // Load configuration
            try (FileInputStream in = new FileInputStream(configFile)) {
                config.load(in);
            }
            
            // Get database properties
            String host = config.getProperty("database.host", "127.0.0.1");
            String port = config.getProperty("database.port", "3306");
            String dbName = config.getProperty("database.name", "loginsystem");
            String username = config.getProperty("database.username", "root");
            String password = config.getProperty("database.password", "");
            
            // Build JDBC URL
            String jdbcUrl = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true",
                host, port, dbName
            );
            
            System.out.println("Testing database connection...");
            System.out.println("JDBC URL: " + jdbcUrl);
            System.out.println("Username: " + username);
            
            // Test connection
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                System.out.println("✅ Database connection successful!");
                System.out.println("Database: " + conn.getMetaData().getDatabaseProductName() + 
                                 " " + conn.getMetaData().getDatabaseProductVersion());
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Database connection failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("❌ Failed to load configuration file: " + configFile);
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ An unexpected error occurred:");
            e.printStackTrace();
        }
    }
}
