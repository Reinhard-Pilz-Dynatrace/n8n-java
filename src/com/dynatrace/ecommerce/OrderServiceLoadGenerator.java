package com.dynatrace.ecommerce;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load Generator for OrderService.java
 * 
 * This creates realistic transaction load to demonstrate performance issues:
 * - Multiple concurrent users
 * - Continuous queries
 * - Tracks response times
 * - Monitors errors
 * - Perfect for Dynatrace monitoring
 */
public class OrderServiceLoadGenerator {
    
    private static final int DEFAULT_THREADS = 10;
    private static final int DEFAULT_DURATION_SECONDS = 300; // 5 minutes
    private static final int CUSTOMER_ID = 1; // Customer with 50 orders (N+1 problem!)
    
    private final OrderService orderService;
    private final ExecutorService executorService;
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final List<Long> responseTimes = new CopyOnWriteArrayList<>();
    
    private volatile boolean running = false;
    
    public OrderServiceLoadGenerator(int threads) {
        this.orderService = new OrderService();
        this.executorService = Executors.newFixedThreadPool(threads);
    }
    
    /**
     * Start load generation
     */
    public void start(int durationSeconds) {
        running = true;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║        OrderService Load Generator Started                ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("Configuration:");
        System.out.println("  - Threads: " + ((ThreadPoolExecutor)executorService).getCorePoolSize());
        System.out.println("  - Duration: " + durationSeconds + " seconds");
        System.out.println("  - Target Customer ID: " + CUSTOMER_ID + " (50 orders - N+1 problem!)");
        System.out.println("  - Expected queries per request: 101 (1 + 50*2)");
        System.out.println();
        System.out.println("Starting load generation...\n");
        
        // Schedule worker threads
        for (int i = 0; i < ((ThreadPoolExecutor)executorService).getCorePoolSize(); i++) {
            final int threadId = i + 1;
            executorService.submit(() -> workerThread(threadId, endTime));
        }
        
        // Monitor thread - prints stats every 10 seconds
        Thread monitorThread = new Thread(() -> monitorProgress(startTime, endTime));
        monitorThread.setDaemon(true);
        monitorThread.start();
        
        // Wait for completion
        try {
            Thread.sleep(durationSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        stop();
        printFinalReport(startTime);
    }
    
    /**
     * Worker thread that continuously makes requests
     */
    private void workerThread(int threadId, long endTime) {
        while (running && System.currentTimeMillis() < endTime) {
            try {
                long requestStart = System.currentTimeMillis();
                
                // Execute the problematic query (N+1 issue)
                List<Order> orders = orderService.getCustomerOrders(CUSTOMER_ID);
                
                long responseTime = System.currentTimeMillis() - requestStart;
                
                // Record metrics
                totalRequests.incrementAndGet();
                successfulRequests.incrementAndGet();
                totalResponseTime.addAndGet(responseTime);
                responseTimes.add(responseTime);
                
                // Optional: Add think time (simulate real user behavior)
                Thread.sleep(100); // 100ms think time
                
            } catch (Exception e) {
                failedRequests.incrementAndGet();
                totalRequests.incrementAndGet();
                System.err.println("[Thread-" + threadId + "] Error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Monitor and print progress
     */
    private void monitorProgress(long startTime, long endTime) {
        while (running && System.currentTimeMillis() < endTime) {
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
        int total = totalRequests.get();
        int success = successfulRequests.get();
        int failed = failedRequests.get();
        
        double avgResponseTime = total > 0 ? (double) totalResponseTime.get() / total : 0;
        double throughput = elapsed > 0 ? (double) total / elapsed : 0;
        
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.printf("Time: %ds | Requests: %d | Success: %d | Failed: %d%n", 
            elapsed, total, success, failed);
        System.out.printf("Avg Response Time: %.2f ms | Throughput: %.2f req/sec%n", 
            avgResponseTime, throughput);
        System.out.printf("Database Queries Generated: ~%d (%.2f queries/sec)%n", 
            total * 101, total * 101.0 / elapsed);
        System.out.println("─────────────────────────────────────────────────────────────");
    }
    
    /**
     * Stop load generation
     */
    public void stop() {
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Print final report
     */
    private void printFinalReport(long startTime) {
        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        int total = totalRequests.get();
        int success = successfulRequests.get();
        int failed = failedRequests.get();
        
        double avgResponseTime = total > 0 ? (double) totalResponseTime.get() / total : 0;
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
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              LOAD TEST FINAL REPORT                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
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
        System.out.println("  Queries per request: 101 (1 initial + 50 items + 50 shipping)");
        System.out.println("  Total DB queries:    ~" + (total * 101));
        System.out.printf("  Query rate:          %.2f queries/sec%n", (total * 101.0) / totalTime);
        System.out.println();
        System.out.println("EXPECTED DYNATRACE FINDINGS:");
        System.out.println("  ✓ High database response time");
        System.out.println("  ✓ N+1 query pattern detected");
        System.out.println("  ✓ Connection pool pressure");
        System.out.println("  ✓ Memory leak (unclosed connections)");
        System.out.println("  ✓ High CPU usage on database");
        System.out.println();
        System.out.println("╚════════════════════════════════════════════════════════════╝");
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
     */
    public static void main(String[] args) {
        int threads = DEFAULT_THREADS;
        int duration = DEFAULT_DURATION_SECONDS;
        
        // Parse command line arguments
        if (args.length > 0) {
            try {
                threads = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid threads argument, using default: " + DEFAULT_THREADS);
            }
        }
        
        if (args.length > 1) {
            try {
                duration = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid duration argument, using default: " + DEFAULT_DURATION_SECONDS);
            }
        }
        
        // Create and start load generator
        OrderServiceLoadGenerator generator = new OrderServiceLoadGenerator(threads);
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown signal received, stopping load generation...");
            generator.stop();
        }));
        
        // Start the load test
        generator.start(duration);
    }
}
