# Workshop Demo - Setup Complete! ✅

## What We Built

A complete Java workshop application demonstrating **two cascading performance problems** for Dynatrace monitoring:

### Problem 2: Connection Leak (Primary)
- **Location**: `getOrderItems()` and `getShippingInfo()` methods
- **Issue**: Connections not closed 20% of the time
- **Impact**: Pool exhausts after ~50 requests (1-2 minutes)
- **Symptoms**: "Connection pool exhausted" errors, service failure

### Problem 1: N+1 Queries (Hidden)
- **Location**: `getCustomerOrders()` method
- **Issue**: 101 separate database queries instead of 1-3 JOINs
- **Impact**: Hidden until connection leak fixed (low throughput masks it)
- **Symptoms**: 5+ second response times when throughput increases

## Key Innovations

### 1. Transparent Connection Pooling
Instead of explicit `pool.returnConnection(conn)`, we use a wrapper pattern:

```java
// Application code just calls close() normally
try (Connection conn = pool.getConnection()) {
    // use connection
} // close() returns to pool automatically!
```

**Benefits**:
- More realistic code patterns
- Clearer what the fix should be (try-with-resources)
- Makes the leak obvious: just forgot to close!

### 2. Intermittent Leak (20% rate)
```java
if (Math.random() > 0.20) {
    conn.close(); // 80% work fine
}
// 20% leak - gradual degradation
```

**Benefits**:
- Realistic gradual degradation
- Takes 1-2 minutes to exhaust pool
- Mimics real-world scenarios
- Better for workshop demos

### 3. Small Pool Size (10 connections)
```java
MAX_CONNECTIONS = 10  // Makes leaks obvious quickly!
```

**Benefits**:
- Leaks become obvious in minutes, not hours
- Clear error messages
- Easy to observe in Dynatrace

## Architecture

```
WorkshopDemo (main entry point)
    ↓
    Starts: OrderServiceHttpServer (port 8080)
    Starts: OrderServiceLoadGenerator (20 threads)
    ↓
Load Generator → HTTP GET /orders?customerId=1
    ↓
HTTP Server → OrderService.getCustomerOrders()
    ↓
Database ← N+1 queries + Connection leaks
    ↓
Dynatrace monitors everything
```

## Files Created/Modified

### Core Application
- ✅ `OrderService.java` - Service with both problems
- ✅ `OrderServiceHttpServer.java` - JDK HttpServer on port 8080
- ✅ `OrderServiceLoadGenerator.java` - Multi-threaded HTTP client
- ✅ `WorkshopDemo.java` - Single entry point for both

### Connection Pooling
- ✅ `SimpleConnectionPool.java` - Pool manager (max 10)
- ✅ `PooledConnection.java` - Transparent wrapper (intercepts close())

### Database
- ✅ `DatabaseInitializer.java` - H2 setup with 55 orders
- ✅ Schema: orders, order_items, shipping, processed_orders
- ✅ Test data: 50 orders for customer 1, 5 for customer 2

### Data Models
- ✅ `Order.java`
- ✅ `OrderItem.java`
- ✅ `ShippingInfo.java`

### VS Code Configuration
- ✅ `.vscode/launch.json` - Single "Workshop Demo" launch config
- ✅ `.vscode/settings.json` - Java project setup

### Documentation
- ✅ `WORKSHOP.md` - Complete workshop guide
- ✅ `FIXES.md` - Quick fix reference with before/after code
- ✅ `POOLING.md` - Deep dive into connection pooling architecture
- ✅ `README.md` - Original project documentation

## How to Run

### Quick Start
1. Press `F5` in VS Code
2. Launches WorkshopDemo automatically
3. Runs indefinitely until Ctrl+C

### What Happens
1. HTTP server starts on port 8080
2. Wait 3 seconds
3. Load generator starts (20 threads, 50ms think time)
4. Generates continuous HTTP traffic
5. After ~50 requests: connection pool exhausts
6. Service fails with "Connection pool exhausted" errors

### In Dynatrace
1. Process auto-detected
2. Web service auto-detected on port 8080
3. Database calls traced
4. Problem ticket created: "Resource exhaustion"
5. After fix applied: Problem auto-closes

## The Workshop Flow

### Phase 1: Detect Connection Leak
1. Start application (F5)
2. Dynatrace detects service
3. After 1-2 minutes: pool exhausts
4. Errors appear in logs and Dynatrace
5. Problem ticket created

### Phase 2: Fix Connection Leak
**Option A: Simple fix**
```java
// Remove the if statement, always close
conn.close();
```

**Option B: Best practice fix**
```java
// Wrap in try-with-resources
try (Connection conn = pool.getConnection()) {
    // use it
}
```

**Files to fix**: 
- `OrderService.java` line ~134 (`getOrderItems`)
- `OrderService.java` line ~167 (`getShippingInfo`)

### Phase 3: Detect N+1 Queries
1. After connection leak fixed
2. Throughput increases 10x
3. Response times spike to 5+ seconds
4. Database query count explodes (101 per request!)
5. New problem ticket created

### Phase 4: Fix N+1 Queries
Replace separate queries with JOINs:
```java
// Instead of 1 + 50 + 50 queries, use 1 optimized query
SELECT o.*, oi.*, s.*
FROM orders o
LEFT JOIN order_items oi ON o.order_id = oi.order_id
LEFT JOIN shipping s ON o.order_id = s.order_id
WHERE o.customer_id = ?
```

## Key Learning Points

### 1. Resource Management
- Always close JDBC resources (Connection, Statement, ResultSet)
- Use try-with-resources for guaranteed cleanup
- Connection pools need proper return/close logic

### 2. N+1 Query Problem
- Loading parent + children in loops = performance disaster
- Use JOINs or batch loading
- Easy to miss at low volume, critical at scale

### 3. Cascading Problems
- One problem can hide another
- Fixing Problem 2 reveals Problem 1
- Monitor after every fix!

### 4. Observability
- Dynatrace auto-detects services and problems
- Problems have context: traces, metrics, logs
- Automation enables faster resolution

## Testing Your Understanding

### Before Any Fix
- ❓ How many requests before failure? (~50)
- ❓ What's the error message? ("Connection pool exhausted")
- ❓ Why 50 requests? (10 pool / 0.20 leak rate ≈ 50)

### After Connection Leak Fixed
- ❓ Does service still fail? (No)
- ❓ What's slow now? (Response times)
- ❓ How many database queries per request? (101)
- ❓ Why didn't we see this before? (Low throughput from leak)

### After Both Fixed
- ❓ How many database queries now? (1 or 3)
- ❓ What's the response time? (<100ms)
- ❓ Can service run indefinitely? (Yes!)

## Architecture Patterns Demonstrated

1. **Wrapper Pattern** - PooledConnection wraps Connection
2. **Object Pool Pattern** - SimpleConnectionPool manages reusable objects
3. **Try-with-resources** - Automatic resource management
4. **HTTP REST** - Simple JSON API with GET endpoints
5. **Multi-threading** - Load generator with worker threads
6. **Observability** - Dynatrace auto-instrumentation

## Production Readiness Notes

This is a **workshop demo** - for production, use:
- **HikariCP** instead of SimpleConnectionPool
- **Spring Boot** instead of raw HttpServer
- **JPA/Hibernate** instead of raw JDBC
- **Proper error handling** and retries
- **Health checks** and metrics endpoints
- **Connection pool monitoring** and alerting
- **Database connection pooling** at database level

But the core concepts are the same!

## Troubleshooting

### VS Code doesn't recognize Java project
- Check `.vscode/settings.json` exists
- Project name should be `n8n-java_259db738`
- Reload window if needed

### ClassNotFoundException
- Delete `bin/` folder to force recompile
- VS Code will recompile on next run

### Connection errors
- Application auto-falls back to H2 if PostgreSQL unavailable
- Check console for "Connected to PostgreSQL" or "switching to H2"

### No errors in workshop
- Check pool size is 10 (not 100)
- Check leak rate is 20% (Math.random() > 0.20)
- Check load generator uses 20 threads with 50ms think time

## Next Steps

1. **Run the demo** - Press F5 and watch it break!
2. **Open Dynatrace** - See the service auto-detected
3. **Fix Problem 2** - Add try-with-resources
4. **Watch Problem 1 emerge** - Response times spike
5. **Fix Problem 1** - Add JOIN queries
6. **Celebrate** - Everything works! 🎉

## Questions for Workshop Attendees

1. How does Dynatrace detect the HTTP service automatically?
2. Why does fixing Problem 2 reveal Problem 1?
3. What other resource leaks can occur in Java applications?
4. How would you monitor connection pool health in production?
5. What automation could prevent these problems from reaching production?

---

**Ready to run the workshop!** Press F5 and let the problems emerge! 🚀
