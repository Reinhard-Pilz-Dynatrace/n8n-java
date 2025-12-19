package com.dynatrace.ecommerce;

import com.dynatrace.ecommerce.client.OrderServiceLoadGenerator;
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
            
            // Wait for server to be ready
            System.out.println("Waiting for server to initialize...");
            Thread.sleep(3000);
            
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
}
