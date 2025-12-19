package com.dynatrace.ecommerce.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initializes an in-memory H2 database with test data
 * Used as a fallback when PostgreSQL is unavailable
 */
public class DatabaseInitializer { 
    
    public static final String H2_URL = "jdbc:h2:mem:ecommerce;DB_CLOSE_DELAY=-1";
    public static final String H2_USER = "sa";
    public static final String H2_PASSWORD = "";
    
    public static void initializeH2Database() throws SQLException {
        System.out.println("Initializing H2 in-memory database...");
        
        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            // Create orders table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    order_id INT PRIMARY KEY,
                    customer_id INT NOT NULL,
                    order_date DATE NOT NULL,
                    total_amount DECIMAL(10,2) NOT NULL
                )
                """);
            
            // Create order_items table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS order_items (
                    item_id INT PRIMARY KEY,
                    order_id INT NOT NULL,
                    product_name VARCHAR(255) NOT NULL,
                    quantity INT NOT NULL,
                    price DECIMAL(10,2) NOT NULL
                )
                """);
            
            // Create shipping table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shipping (
                    shipping_id INT PRIMARY KEY AUTO_INCREMENT,
                    order_id INT NOT NULL,
                    address VARCHAR(255) NOT NULL,
                    city VARCHAR(100) NOT NULL,
                    state VARCHAR(50) NOT NULL,
                    zip_code VARCHAR(20) NOT NULL,
                    tracking_number VARCHAR(100) NOT NULL
                )
                """);
            
            // Create processed_orders table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS processed_orders (
                    order_id INT PRIMARY KEY,
                    status VARCHAR(50) NOT NULL
                )
                """);
            
            // Insert test data - Customer 1 with 500 orders (for N+1 problem demonstration)
            System.out.println("Inserting test data for customer 1 (500 orders)...");
            for (int i = 1; i <= 500; i++) {
                // Insert order
                double totalAmount = 100.0 + (i * 10.5);
                String totalAmountStr = String.valueOf(totalAmount).replace(',', '.');
                stmt.execute(
                    "INSERT INTO orders VALUES (" + i + ", 1, CURRENT_DATE(), " + totalAmountStr + ")"
                );
                
                // Insert 3 items per order
                for (int j = 1; j <= 3; j++) {
                    int itemId = (i - 1) * 3 + j;
                    double price = 19.99 + j;
                    String priceStr = String.valueOf(price).replace(',', '.');
                    stmt.execute(
                        "INSERT INTO order_items VALUES (" + itemId + ", " + i + ", 'Product-" + i + "-" + j + "', " + j + ", " + priceStr + ")"
                    );
                }
                
                // Insert shipping info
                stmt.execute(
                    "INSERT INTO shipping (order_id, address, city, state, zip_code, tracking_number) " +
                    "VALUES (" + i + ", '" + (100 + i) + " Main St', 'Springfield', 'IL', '62701', 'TRACK-" + i + "')"
                );
            }
            
            // Add a few orders for customer 2 (for variety)
            System.out.println("Inserting test data for customer 2 (50 orders)...");
            for (int i = 501; i <= 550; i++) {
                double totalAmount = 75.0 + (i * 5.0);
                String totalAmountStr = String.valueOf(totalAmount).replace(',', '.');
                stmt.execute(
                    "INSERT INTO orders VALUES (" + i + ", 2, CURRENT_DATE(), " + totalAmountStr + ")"
                );
                
                for (int j = 1; j <= 2; j++) {
                    int itemId = (i - 1) * 3 + j;
                    double price = 15.99 + j;
                    String priceStr = String.valueOf(price).replace(',', '.');
                    stmt.execute(
                        "INSERT INTO order_items VALUES (" + itemId + ", " + i + ", 'Product-" + i + "-" + j + "', " + j + ", " + priceStr + ")"
                    );
                }
                
                stmt.execute(
                    "INSERT INTO shipping (order_id, address, city, state, zip_code, tracking_number) " +
                    "VALUES (" + i + ", '" + (200 + i) + " Oak Ave', 'Chicago', 'IL', '60601', 'TRACK-" + i + "')"
                );
            }
            
            System.out.println("* H2 database initialized successfully!");
            System.out.println("  - 550 orders created (500 for customer 1, 50 for customer 2)");
            System.out.println("  - 1650 order items");
            System.out.println("  - 550 shipping records");
        }
    }
    
    public static void main(String[] args) {
        try {
            initializeH2Database();
            System.out.println("\nTest query:");
            try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);
                 Statement stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM orders WHERE customer_id = 1")) {
                if (rs.next()) {
                    System.out.println("Orders for customer 1: " + rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
