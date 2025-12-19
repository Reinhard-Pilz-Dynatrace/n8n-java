package com.dynatrace.ecommerce.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Simple connection pool with configurable limit
 * Designed to demonstrate connection leak issues
 */
public class SimpleConnectionPool {
    
    private static final int MAX_CONNECTIONS = 200;
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    
    private final BlockingQueue<PooledConnection> availableConnections;
    private final String url;
    private final String user;
    private final String password;
    private int totalCreated = 0;
    
    public SimpleConnectionPool(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.availableConnections = new ArrayBlockingQueue<>(MAX_CONNECTIONS);
        
        System.out.println("Connection Pool initialized with MAX " + MAX_CONNECTIONS + " connections");
    }
    
    /**
     * Get a connection from the pool
     * Will wait up to CONNECTION_TIMEOUT_SECONDS if pool is exhausted
     */
    public Connection getConnection() throws SQLException {
        try {
            // Try to get an existing connection
            PooledConnection conn = availableConnections.poll();
            
            if (conn == null && totalCreated < MAX_CONNECTIONS) {
                // No connection available but under limit - create new one immediately
                synchronized (this) {
                    if (totalCreated < MAX_CONNECTIONS) {
                        Connection realConnection = DriverManager.getConnection(url, user, password);
                        conn = new PooledConnection(realConnection, this);
                        totalCreated++;
                        System.out.println("Created new connection. Total: " + totalCreated + "/" + MAX_CONNECTIONS);
                    }
                }
            }
            
            if (conn == null) {
                // Pool is at MAX - wait for a connection to be returned
                System.err.println("⚠ WARNING: Connection pool at maximum! Waiting for available connection...");
                conn = availableConnections.poll(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                
                if (conn == null) {
                    throw new SQLException(
                        "Connection pool exhausted! " +
                        "No connections available after " + CONNECTION_TIMEOUT_SECONDS + " seconds. " +
                        "Possible connection leak - connections not being returned to pool!"
                    );
                }
            }
            
            // Mark as active (not closed)
            conn.reopen();
            return conn;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }
    }
    
    /**
     * Return a connection to the pool
     * Called automatically by PooledConnection.close()
     */
    void returnConnection(PooledConnection conn) {
        if (conn != null) {
            boolean returned = availableConnections.offer(conn);
            if (!returned) {
                System.err.println("⚠ Failed to return connection to pool - pool full!");
                try {
                    conn.reallyClose();
                } catch (SQLException e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Get pool statistics
     */
    public String getStats() {
        return String.format(
            "Pool: %d available / %d total (max: %d)",
            availableConnections.size(),
            totalCreated,
            MAX_CONNECTIONS
        );
    }
    
    /**
     * Close all connections in the pool
     */
    public void shutdown() {
        System.out.println("Shutting down connection pool...");
        PooledConnection conn;
        while ((conn = availableConnections.poll()) != null) {
            try {
                conn.reallyClose();
            } catch (SQLException e) {
                // Ignore
            }
        }
        System.out.println("Connection pool shutdown complete");
    }
}
