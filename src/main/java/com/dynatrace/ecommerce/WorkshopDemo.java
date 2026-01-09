package com.dynatrace.ecommerce;

import com.dynatrace.ecommerce.client.OrderServiceLoadGenerator;
import com.dynatrace.ecommerce.client.SimpleHttpClient;
import com.dynatrace.ecommerce.server.OrderServiceHttpServer;

/**
 * Main entry point for the OrderService workshop demo
 * Starts both HTTP server and load generator in one process
 */
public class WorkshopDemo {
    
    public static void main(String[] args) {
        try {
            System.out.println("+============================================================+");
            System.out.println("|     OrderService Workshop Demo - Starting...              |");
            System.out.println("+============================================================+");
            System.out.println();
            
            // Step 1: Start HTTP Server in separate thread
            System.out.println("Step 1: Starting HTTP Server...");
            OrderServiceHttpServer server = new OrderServiceHttpServer();
            
            Thread serverThread = new Thread(() -> {
                server.start();
            });
            serverThread.setDaemon(false);
            serverThread.start();
            
            // Wait for server to be ready - increased delay for DB initialization
            System.out.println("Waiting for server to initialize (database, connection pool, etc.)...");
            Thread.sleep(15000); // 15 seconds to ensure full initialization
            
            // Verify server is responding to health checks
            System.out.println("Verifying server health...");
            boolean serverReady = waitForServerHealth(30); // Wait up to 30 seconds
            
            if (!serverReady) {
                System.err.println("WARNING: Server health check failed, but continuing anyway...");
            } else {
                System.out.println("✓ Server is healthy and ready");
            }
            
            // Step 2: Start Load Generator
            System.out.println();
            System.out.println("Step 2: Starting Load Generator...");
            System.out.println();
            
            OrderServiceLoadGenerator loadGenerator = new OrderServiceLoadGenerator();
            
            // Register shutdown hook to stop server gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println();
                System.out.println("Shutting down...");
                loadGenerator.stop();
                server.stop();
                System.out.println("Demo stopped.");
            }));
            
            System.out.println("Running indefinitely - press Ctrl+C to stop...");
            System.out.println();
            loadGenerator.start();
            
        } catch (Exception e) {
            System.err.println("Error starting demo: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Wait for server to respond to health checks
     * Uses SimpleHttpClient to bypass Dynatrace instrumentation
     * @param maxWaitSeconds maximum seconds to wait
     * @return true if server is healthy, false if timeout
     */
    private static boolean waitForServerHealth(int maxWaitSeconds) {
        int attempts = 0;
        int maxAttempts = maxWaitSeconds;
        SimpleHttpClient httpClient = new SimpleHttpClient("localhost", 8080);
        
        while (attempts < maxAttempts) {
            try {
                String response = httpClient.get("/health");
                // If we get a response without exception, server is healthy
                if (response != null) {
                    return true;
                }
            } catch (Exception e) {
                // Server not ready yet, continue waiting
            }
            
            attempts++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }
}
