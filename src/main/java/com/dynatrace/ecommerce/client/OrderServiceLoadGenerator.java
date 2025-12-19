package com.dynatrace.ecommerce.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Load Generator for OrderService HTTP Server
 * 
 * This creates realistic HTTP transaction load to demonstrate performance issues:
 * - Single thread with configurable think time (simulates real user)
 * - Continuous queries
 * - Tracks response times
 * - Monitors errors
 * - Uses raw socket HTTP client (bypasses Dynatrace auto-instrumentation)
 * - Perfect for Dynatrace monitoring
 */
public class OrderServiceLoadGenerator {
    
    private static final int DEFAULT_THINK_TIME_MS = 5000; // Simulated user think time
    private static final int CUSTOMER_ID = 1; // Customer with ~500 orders (N+1 problem!)
    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    
    private final SimpleHttpClient httpClient;
    private int totalRequests = 0;
    private int successfulRequests = 0;
    private int failedRequests = 0;
    private long totalResponseTime = 0;
    private final List<Long> responseTimes = new ArrayList<>();
    
    private volatile boolean running = false;
    
    public OrderServiceLoadGenerator() {
        this.httpClient = new SimpleHttpClient(HOST, PORT);
    }
    
    /**
     * Start load generation
     */
    public void start() {
        running = true;
        long startTime = System.currentTimeMillis();
        
        System.out.println("+============================================================+");
        System.out.println("|        OrderService Load Generator Started                |");
        System.out.println("+============================================================+");
        System.out.println("Configuration:");
        System.out.println("  - Mode: Single thread");
        System.out.println("  - Think time: " + (DEFAULT_THINK_TIME_MS / 1000) + " seconds");
        System.out.println("  - Target Customer ID: " + CUSTOMER_ID + " (~500 orders - N+1 problem!)");
        System.out.println("  - Expected queries per request: ~1001 (1 + 500*2)");
        System.out.println();
        System.out.println("Starting load generation...\n");
        
        // Start monitoring thread
        Thread monitorThread = new Thread(() -> monitorProgress(startTime));
        monitorThread.setDaemon(true);
        monitorThread.start();
        
        // Single worker thread - simulate one user
        workerThread(startTime);
    }
    
    /**
     * Worker thread that continuously makes HTTP requests
     */
    private void workerThread(long startTime) {
        int consecutiveFailures = 0;
        final int MAX_CONSECUTIVE_FAILURES = 10;
        
        while (running) {
            try {
                long requestStart = System.currentTimeMillis();
                
                // Make HTTP GET request using raw socket client
                String path = "/orders?customerId=" + CUSTOMER_ID;
                String response = httpClient.get(path);
                
                long responseTime = System.currentTimeMillis() - requestStart;
                
                // Record metrics
                totalRequests++;
                if (response != null && !response.isEmpty()) {
                    successfulRequests++;
                    consecutiveFailures = 0; // Reset failure counter on success
                } else {
                    failedRequests++;
                    consecutiveFailures++;
                }
                totalResponseTime += responseTime;
                responseTimes.add(responseTime);
                
                // If too many consecutive failures, wait longer before retry
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    System.err.println("Too many consecutive failures, waiting 30 seconds before retry...");
                    Thread.sleep(30000);
                    consecutiveFailures = 0;
                } else {
                    // Think time - simulate user behavior
                    Thread.sleep(DEFAULT_THINK_TIME_MS);
                }
                
            } catch (Exception e) {
                failedRequests++;
                totalRequests++;
                consecutiveFailures++;
                System.err.println("Error: " + e.getMessage());
                
                // Exponential backoff on errors
                try {
                    long backoffTime = Math.min(30000, 1000 * consecutiveFailures);
                    Thread.sleep(backoffTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        printFinalReport(startTime);
    }
    
    /**
     * Monitor and print progress
     */
    private void monitorProgress(long startTime) {
        while (running) {
            try {
                Thread.sleep(10000); // Print every 10 seconds
                printStats(startTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Print current statistics
     */
    private void printStats(long startTime) {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        int total = totalRequests;
        int success = successfulRequests;
        int failed = failedRequests;
        
        double avgResponseTime = total > 0 ? (double) totalResponseTime / total : 0;
        double throughput = elapsed > 0 ? (double) total / elapsed : 0;
        
        System.out.println("-------------------------------------------------------------");
        System.out.printf("Time: %ds | Requests: %d | Success: %d | Failed: %d%n", 
            elapsed, total, success, failed);
        System.out.printf("Avg Response Time: %.2f ms | Throughput: %.2f req/sec%n", 
            avgResponseTime, throughput);
        System.out.printf("Database Queries Generated: ~%d (%.2f queries/sec)%n", 
            total * 1001, total * 1001.0 / elapsed);
        System.out.println("-------------------------------------------------------------");
    }
    
    /**
     * Stop load generation
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Print final report
     */
    private void printFinalReport(long startTime) {
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        int total = totalRequests;
        int success = successfulRequests;
        int failed = failedRequests;
        
        double avgResponseTime = total > 0 ? (double) totalResponseTime / total : 0;
        double throughput = totalTime > 0 ? (double) total / totalTime : 0;
        
        // Calculate percentiles
        List<Long> sortedTimes = new ArrayList<>(responseTimes);
        sortedTimes.sort(Long::compareTo);
        
        long p50 = getPercentile(sortedTimes, 50);
        long p95 = getPercentile(sortedTimes, 95);
        long p99 = getPercentile(sortedTimes, 99);
        long min = sortedTimes.isEmpty() ? 0 : sortedTimes.get(0);
        long max = sortedTimes.isEmpty() ? 0 : sortedTimes.get(sortedTimes.size() - 1);
        
        System.out.println();
        System.out.println("+============================================================+");
        System.out.println("|              LOAD TEST FINAL REPORT                        |");
        System.out.println("+============================================================+");
        System.out.println();
        System.out.println("Test Duration: " + totalTime + " seconds");
        System.out.println();
        System.out.println("REQUESTS:");
        System.out.println("  Total Requests:      " + total);
        System.out.println("  Successful:          " + success + " (" + (success * 100.0 / total) + "%)");
        System.out.println("  Failed:              " + failed + " (" + (failed * 100.0 / total) + "%)");
        System.out.println();
        System.out.println("PERFORMANCE:");
        System.out.printf("  Throughput:          %.2f requests/sec%n", throughput);
        System.out.printf("  Avg Response Time:   %.2f ms%n", avgResponseTime);
        System.out.println("  Min Response Time:   " + min + " ms");
        System.out.println("  Max Response Time:   " + max + " ms");
        System.out.println();
        System.out.println("RESPONSE TIME PERCENTILES:");
        System.out.println("  50th percentile:     " + p50 + " ms");
        System.out.println("  95th percentile:     " + p95 + " ms");
        System.out.println("  99th percentile:     " + p99 + " ms");
        System.out.println();
        System.out.println("DATABASE IMPACT (N+1 Problem):");
        System.out.println("  Queries per request: ~1001 (1 initial + 500 items + 500 shipping)");
        System.out.println("  Total DB queries:    ~" + (total * 1001));
        System.out.printf("  Query rate:          %.2f queries/sec%n", (total * 1001.0) / totalTime);
        System.out.println();
        System.out.println("EXPECTED DYNATRACE FINDINGS:");
        System.out.println("  * High database response time");
        System.out.println("  * N+1 query pattern detected");
        System.out.println("  * Connection pool pressure");
        System.out.println("  * Memory leak (unclosed connections)");
        System.out.println("  * High CPU usage on database");
        System.out.println();
        System.out.println("+============================================================+");
    }
    
    /**
     * Calculate percentile
     */
    private long getPercentile(List<Long> sortedList, int percentile) {
        if (sortedList.isEmpty()) return 0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));
        return sortedList.get(index);
    }
    
    /**
     * Main method - Run the load test
     * 
     * IMPORTANT: Make sure OrderServiceHttpServer is running first!
     * Run OrderServiceHttpServer.main() in a separate terminal/debug session
     */
    public static void main(String[] args) {
        System.out.println("+============================================================+");
        System.out.println("|  OrderService Load Generator - Single Thread Mode         |");
        System.out.println("+------------------------------------------------------------+");
        System.out.println("|  Prerequisites:                                            |");
        System.out.println("|  * OrderServiceHttpServer must be running on port 8080     |");
        System.out.println("|  * Start it first: OrderServiceHttpServer.main()           |");
        System.out.println("+============================================================+");
        System.out.println();
        
        // Create and start load generator
        OrderServiceLoadGenerator generator = new OrderServiceLoadGenerator();
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown signal received, stopping load generation...");
            generator.stop();
        }));
        
        // Start the load test
        generator.start();
    }
}
