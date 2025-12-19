package com.dynatrace.ecommerce;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dynatrace.ecommerce.db.DatabaseInitializer;
import com.dynatrace.ecommerce.db.SimpleConnectionPool;

/**
 * Order Service with performance issues for workshop demonstration:
 * 1. N+1 Query Problem - Multiple database calls in a loop
 * 2. Connection Leak - Connections not properly closed in some code paths
 */
public class OrderService {
    
    // Database configuration - uses H2 in-memory database
    private static final String DB_URL = DatabaseInitializer.H2_URL;
    private static final String DB_USER = DatabaseInitializer.H2_USER;
    private static final String DB_PASSWORD = DatabaseInitializer.H2_PASSWORD;
    
    // Connection pool with configurable limit
    private static SimpleConnectionPool connectionPool;
    
    static {
        // Initialize H2 database with test data
        try {
            DatabaseInitializer.initializeH2Database();
            System.out.println("* Database initialized successfully");
        } catch (SQLException e) {
            System.err.println("✗ Failed to initialize database!");
            e.printStackTrace();
        }
        
        // Initialize connection pool
        connectionPool = new SimpleConnectionPool(DB_URL, DB_USER, DB_PASSWORD);
    }
    
    /**
     * PROBLEM 1: N+1 Query Problem
     * This method causes a performance issue by making separate database calls
     * for each order's details instead of using a JOIN query.
     * 
     * NOTE: This method properly uses try-with-resources to manage connections
     */
    public List<Order> getCustomerOrders(int customerId) throws SQLException {
        List<Order> orders = new ArrayList<>();
        
        // GOOD PRACTICE: Using try-with-resources to automatically close connection
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT order_id, order_date, total_amount FROM orders WHERE customer_id = ?")) {
            
            stmt.setInt(1, customerId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Order order = new Order();
                    order.setOrderId(rs.getInt("order_id"));
                    order.setOrderDate(rs.getDate("order_date"));
                    order.setTotalAmount(rs.getDouble("total_amount"));
                    
                    // ISSUE: N+1 Problem - Separate query for each order's items
                    order.setItems(getOrderItems(order.getOrderId()));
                    
                    // ISSUE: Another separate query for shipping info
                    order.setShippingInfo(getShippingInfo(order.getOrderId()));
                    
                    orders.add(order);
                }
            }
        }
        
        return orders;
    }
    
    /**
     * Router method for getting order items
     * Uses different implementations based on order characteristics
     * (In reality, this code duplication introduced a bug in one path)
     */
    private List<OrderItem> getOrderItems(int orderId) throws SQLException {
        // Business rule: Orders divisible by 5 require extra validation
        // (legacy requirement from audit compliance team)
        if (requiresValidation(orderId)) {
            return getOrderItemsWithValidation(orderId);
        } else {
            return getOrderItemsStandard(orderId);
        }
    }
    
    /**
     * Check if order requires validation
     * Legacy business rule: Every 20th order needs extra validation
     */
    private boolean requiresValidation(int orderId) {
        return orderId % 500 == 0;
    }
    
    /**
     * Standard code path for fetching order items
     * GOOD: Properly uses try-with-resources
     */
    private List<OrderItem> getOrderItemsStandard(int orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        
        // GOOD PRACTICE: Using try-with-resources
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT item_id, product_name, quantity, price FROM order_items WHERE order_id = ?")) {
            
            stmt.setInt(1, orderId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setItemId(rs.getInt("item_id"));
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setPrice(rs.getDouble("price"));
                    items.add(item);
                }
            }
        }
        
        return items;
    }
    
    /**
     * "Enhanced" code path with validation (someone's "improvement" that introduced a bug)
     * PROBLEM: Forgets to close connection!
     */
    private List<OrderItem> getOrderItemsWithValidation(int orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        
        // PROBLEM: Connection not wrapped in try-with-resources!
        Connection conn = connectionPool.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT item_id, product_name, quantity, price FROM order_items WHERE order_id = ?"
        );
        stmt.setInt(1, orderId);
        ResultSet rs = stmt.executeQuery();
        
        while (rs.next()) {
            OrderItem item = new OrderItem();
            item.setItemId(rs.getInt("item_id"));
            item.setProductName(rs.getString("product_name"));
            item.setQuantity(rs.getInt("quantity"));
            item.setPrice(rs.getDouble("price"));
            
            // "Validation logic" - checking for valid data
            if (item.getQuantity() > 0 && item.getPrice() > 0) {
                items.add(item);
            }
        }
        
        rs.close();
        stmt.close();
        // BUG: Forgot to close connection! Copy-paste error?
        // Developer probably got distracted after adding validation logic
        
        return items;
    }
    
    /**
     * Router method for getting shipping info
     * Uses different implementations based on shipping requirements
     */
    private ShippingInfo getShippingInfo(int orderId) throws SQLException {
        // Business rule: High-value orders need detailed address formatting
        // (requirement from shipping partner for orders ending in 0 or 5)
        if (requiresDetailedShipping(orderId)) {
            return getShippingInfoDetailed(orderId);
        } else {
            return getShippingInfoFast(orderId);
        }
    }
    
    /**
     * Check if order requires detailed shipping processing
     * Legacy requirement: Every 20th order needs enhanced address formatting
     */
    private boolean requiresDetailedShipping(int orderId) {
        return orderId % 20 == 0;
    }
    
    /**
     * Fast path for shipping info retrieval
     * GOOD: Properly uses try-with-resources
     */
    private ShippingInfo getShippingInfoFast(int orderId) throws SQLException {
        // GOOD PRACTICE: Using try-with-resources
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT address, city, state, zip_code, tracking_number FROM shipping WHERE order_id = ?")) {
            
            stmt.setInt(1, orderId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ShippingInfo info = new ShippingInfo();
                    info.setAddress(rs.getString("address"));
                    info.setCity(rs.getString("city"));
                    info.setState(rs.getString("state"));
                    info.setZipCode(rs.getString("zip_code"));
                    info.setTrackingNumber(rs.getString("tracking_number"));
                    return info;
                }
            }
        }
        
        return null;
    }
    
    /**
     * "Detailed" path with address formatting (another developer's "enhancement")
     * PROBLEM: Forgets to close connection!
     */
    private ShippingInfo getShippingInfoDetailed(int orderId) throws SQLException {
        // PROBLEM: Connection not wrapped in try-with-resources!
        Connection conn = connectionPool.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT address, city, state, zip_code, tracking_number FROM shipping WHERE order_id = ?"
        );
        stmt.setInt(1, orderId);
        ResultSet rs = stmt.executeQuery();
        
        ShippingInfo info = null;
        if (rs.next()) {
            info = new ShippingInfo();
            info.setAddress(rs.getString("address"));
            info.setCity(rs.getString("city"));
            info.setState(rs.getString("state"));
            info.setZipCode(rs.getString("zip_code"));
            info.setTrackingNumber(rs.getString("tracking_number"));
            
            // "Enhancement": Format address nicely
            String formattedAddress = info.getAddress().trim().toUpperCase();
            info.setAddress(formattedAddress);
        }
        
        rs.close();
        stmt.close();
        // BUG: Forgot to close connection! Another copy-paste error
        // Developer was focused on the address formatting "feature"
        
        return info;
    }
    
    /**
     * PROBLEM 4: Inefficient string concatenation in loop
     */
    public String generateOrderReport(List<Order> orders) {
        String report = "";  // ISSUE: Using String concatenation instead of StringBuilder
        
        for (Order order : orders) {
            report += "Order ID: " + order.getOrderId() + "\n";
            report += "Date: " + order.getOrderDate() + "\n";
            report += "Total: $" + order.getTotalAmount() + "\n";
            
            // ISSUE: Nested loop with string concatenation
            for (OrderItem item : order.getItems()) {
                report += "  - " + item.getProductName() + " x" + item.getQuantity() + "\n";
            }
            
            report += "------------------------\n";
        }
        
        return report;
    }
    
    /**
     * PROBLEM 5: Synchronous blocking operations
     */
    public void processOrder(Order order) throws Exception {
        // ISSUE: Blocking call without timeout
        Thread.sleep(5000);  // Simulating slow external API call
        
        // ISSUE: No error handling, no retry logic
        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO processed_orders (order_id, status) VALUES (?, ?)"
        );
        stmt.setInt(1, order.getOrderId());
        stmt.setString(2, "PROCESSED");
        stmt.executeUpdate();
        
        // ISSUE: Resources not closed
    }
    
    /**
     * Test connection - useful for debugging
     */
    public static void testConnection() {
        System.out.println("Testing database connection...");
        System.out.println("DB_URL: " + DB_URL);
        System.out.println("DB_USER: " + DB_USER);
        System.out.println("DB_PASSWORD: " + (DB_PASSWORD != null && !DB_PASSWORD.isEmpty() ? "***SET***" : "***NOT SET***"));
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("✓ Connection successful!");
            System.out.println("Database: " + conn.getCatalog());
            System.out.println("User: " + conn.getMetaData().getUserName());
        } catch (SQLException e) {
            System.err.println("✗ Connection failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        testConnection();
    }
}
