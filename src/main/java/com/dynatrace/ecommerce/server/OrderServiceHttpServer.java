package com.dynatrace.ecommerce.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.List;

import com.dynatrace.ecommerce.Order;
import com.dynatrace.ecommerce.OrderService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * HTTP Server for OrderService
 * Exposes REST endpoints for order retrieval
 * Dynatrace will automatically monitor this as a web service
 */
public class OrderServiceHttpServer {
    
    private static final int PORT = 8080;
    private final HttpServer server;
    private final OrderService orderService;
    
    public OrderServiceHttpServer() throws IOException {
        this.orderService = new OrderService();
        this.server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Register endpoints
        server.createContext("/orders", new OrdersHandler());
        server.createContext("/health", new HealthHandler());
        server.createContext("/shutdown", new ShutdownHandler());
        
        server.setExecutor(null); // Use default executor
    }
    
    public void start() {
        server.start();
        System.out.println("===========================================");
        System.out.println("OrderService HTTP Server started on port " + PORT);
        System.out.println("Endpoints:");
        System.out.println("  GET /orders?customerId=<id>");
        System.out.println("  GET /health");
        System.out.println("  POST /shutdown");
        System.out.println("===========================================");
    }
    
    public void stop() {
        server.stop(0);
        System.out.println("OrderService HTTP Server stopped");
    }
    
    /**
     * Handler for /orders endpoint
     * GET /orders?customerId=1
     */
    class OrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            
            try {
                // Parse customerId from query string
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.startsWith("customerId=")) {
                    sendResponse(exchange, 400, "Missing customerId parameter");
                    return;
                }
                
                int customerId = Integer.parseInt(query.substring("customerId=".length()));
                
                // Call OrderService to get orders (this will trigger N+1 problem)
                long startTime = System.currentTimeMillis();
                List<Order> orders = orderService.getCustomerOrders(customerId);
                long duration = System.currentTimeMillis() - startTime;
                
                // Generate simple JSON response
                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append("  \"customerId\": ").append(customerId).append(",\n");
                json.append("  \"orderCount\": ").append(orders.size()).append(",\n");
                json.append("  \"queryTimeMs\": ").append(duration).append(",\n");
                json.append("  \"orders\": [\n");
                
                for (int i = 0; i < orders.size(); i++) {
                    Order order = orders.get(i);
                    json.append("    {\n");
                    json.append("      \"orderId\": ").append(order.getOrderId()).append(",\n");
                    json.append("      \"totalAmount\": ").append(order.getTotalAmount()).append(",\n");
                    json.append("      \"itemCount\": ").append(order.getItems().size()).append("\n");
                    json.append("    }");
                    if (i < orders.size() - 1) json.append(",");
                    json.append("\n");
                }
                
                json.append("  ]\n");
                json.append("}\n");
                
                sendResponse(exchange, 200, json.toString());
                
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "Invalid customerId: " + e.getMessage());
            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
                sendResponse(exchange, 500, "Database error: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handler for /health endpoint
     */
    class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\": \"UP\", \"service\": \"OrderService\"}";
            sendResponse(exchange, 200, response);
        }
    }
    
    /**
     * Handler for /shutdown endpoint
     * POST /shutdown - Gracefully shuts down the server
     */
    class ShutdownHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed - use POST");
                return;
            }
            
            String response = "{\"status\": \"shutting down\", \"message\": \"Server shutdown initiated\"}";
            sendResponse(exchange, 200, response);
            
            // Shutdown in a separate thread to allow response to be sent
            new Thread(() -> {
                try {
                    Thread.sleep(500); // Give time for response to be sent
                    System.out.println("\n!!! Shutdown requested via HTTP endpoint !!!");
                    System.exit(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
    
    /**
     * Helper method to send HTTP response
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", 
            statusCode == 200 && response.startsWith("{") ? "application/json" : "text/plain");
        
        byte[] bytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    public static void main(String[] args) {
        try {
            OrderServiceHttpServer server = new OrderServiceHttpServer();
            server.start();
            
            // Keep server running
            System.out.println("Press Ctrl+C to stop the server...");
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
