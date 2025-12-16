package com.dynatrace.ecommerce;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Order Service with multiple performance issues that Dynatrace will detect:
 * 1. N+1 Query Problem - Multiple database calls in a loop
 * 2. Memory Leak - ResultSet and Connection not properly closed
 * 3. Inefficient String Concatenation in loop
 * 4. Blocking I/O operations
 * 5. No connection pooling
 */
public class OrderService {
    
    // Database configuration - reads from environment variables or uses defaults
    private static final String DB_HOST = System.getenv("DB_HOST") != null 
        ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null 
        ? System.getenv("DB_PORT") : "5432";
    private static final String DB_NAME = System.getenv("DB_NAME") != null 
        ? System.getenv("DB_NAME") : "ecommerce";
    private static final String DB_USER = System.getenv("DB_USER") != null 
        ? System.getenv("DB_USER") : "admin";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null 
        ? System.getenv("DB_PASSWORD") : "password";
    
    private static final String DB_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    
    // Debug flag - set to true to print connection info
    private static final boolean DEBUG = Boolean.parseBoolean(
        System.getenv("DEBUG") != null ? System.getenv("DEBUG") : "false"
    );
    
    static {
        if (DEBUG) {
            System.out.println("=== OrderService Database Configuration ===");
            System.out.println("DB_URL: " + DB_URL);
            System.out.println("DB_USER: " + DB_USER);
            System.out.println("DB_PASSWORD: " + (DB_PASSWORD != null ? "***SET***" : "***NOT SET***"));
            System.out.println("==========================================");
        }
    }
    
    /**
     * PROBLEM 1: N+1 Query Problem
     * This method causes a performance issue by making separate database calls
     * for each order's details instead of using a JOIN query.
     */
    public List<Order> getCustomerOrders(int customerId) throws SQLException {
        List<Order> orders = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            // ISSUE: Creating new connection every time instead of using connection pool
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // First query to get all orders
            stmt = conn.prepareStatement("SELECT order_id, order_date, total_amount FROM orders WHERE customer_id = ?");
            stmt.setInt(1, customerId);
            rs = stmt.executeQuery();
            
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
        } finally {
            // ISSUE: Resource leak - resources not closed in case of exception
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
        
        return orders;
    }
    
    /**
     * PROBLEM 2: Separate database call for order items
     */
    private List<OrderItem> getOrderItems(int orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
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
            items.add(item);
        }
        
        // ISSUE: Connections and statements never closed - MEMORY LEAK!
        return items;
    }
    
    /**
     * PROBLEM 3: Another separate database call for shipping info
     */
    private ShippingInfo getShippingInfo(int orderId) throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
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
        }
        
        // ISSUE: Another resource leak!
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

// Supporting classes
class Order {
    private int orderId;
    private java.util.Date orderDate;
    private double totalAmount;
    private List<OrderItem> items;
    private ShippingInfo shippingInfo;
    
    // Getters and setters
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public java.util.Date getOrderDate() { return orderDate; }
    public void setOrderDate(java.util.Date orderDate) { this.orderDate = orderDate; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public ShippingInfo getShippingInfo() { return shippingInfo; }
    public void setShippingInfo(ShippingInfo shippingInfo) { this.shippingInfo = shippingInfo; }
}

class OrderItem {
    private int itemId;
    private String productName;
    private int quantity;
    private double price;
    
    // Getters and setters
    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}

class ShippingInfo {
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String trackingNumber;
    
    // Getters and setters
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
}
