# OrderService Workshop - Problem Resolution Demo

## Overview
This Java application simulates a problematic e-commerce order service for demonstrating Dynatrace monitoring, problem detection, and automated remediation workflows.

## Architecture
- **HTTP Server**: JDK built-in HttpServer on port 8080
- **Endpoints**: 
  - `GET /orders?customerId=<id>` - Retrieve customer orders
  - `GET /health` - Health check
- **Database**: H2 in-memory (auto-fallback from PostgreSQL)
- **Monitoring**: Dynatrace OneAgent automatically detects the web service

## Two Cascading Problems

### Problem 2: Connection Leak (Emerges First) 🔴
**Location**: `OrderService.getOrderItems()` and `OrderService.getShippingInfo()`

**Issue**: Database connections are never closed, causing resource exhaustion

**Symptoms**:
- After ~50 HTTP requests, new requests start failing
- "Cannot get connection" errors
- Connection pool exhausted
- Service becomes unresponsive

**How to Detect in Dynatrace**:
- Database connection metrics spike
- Error rate increases
- Response time degrades to timeouts
- Resource exhaustion alerts

**Root Cause**: The code has **two similar helper methods** - one properly closes connections, the other doesn't. Due to "optimization logic", 80% of requests use the correct method, 20% use the buggy one. This is a classic copy-paste bug where a developer duplicated code and forgot to close resources in one path.

**The Code Pattern**:
```java
// Router method (looks innocent)
private List<OrderItem> getOrderItems(int orderId) {
    if (Math.random() > 0.20) {
        return getOrderItemsStandard(orderId);      // 80% - works fine
    } else {
        return getOrderItemsWithValidation(orderId); // 20% - LEAKS!
    }
}

// Method 1: GOOD - uses try-with-resources
private List<OrderItem> getOrderItemsStandard(int orderId) {
    try (Connection conn = pool.getConnection()) {
        // ... query and process ...
        return items;
    } // Connection automatically closed
}

// Method 2: BAD - forgets to close connection
private List<OrderItem> getOrderItemsWithValidation(int orderId) {
    Connection conn = pool.getConnection();
    PreparedStatement stmt = conn.prepareStatement(...);
    ResultSet rs = stmt.executeQuery();
    // ... query and process with "validation logic" ...
    rs.close();
    stmt.close();
    // BUG: Forgot conn.close()!
    return items;
}
```

**Why It's Realistic**: This mimics real-world scenarios where:
- Code duplication leads to inconsistent resource management
- One developer writes correct code, another copies it but introduces a bug
- "Optimizations" or "enhancements" break working code
- The bug is hidden in a less-frequently executed code path
- The flaw isn't obvious at first glance (no Math.random() in business logic)

**Fix**: Use try-with-resources to ensure connections are always closed
```java
// FIXED (always closes):
try (Connection conn = connectionPool.getConnection();
     PreparedStatement stmt = conn.prepareStatement(...);
     ResultSet rs = stmt.executeQuery()) {
    // ... process results ...
    return items;
} // Automatically closes all resources, returning connection to pool
```

**Technical Detail**: The connection pool uses a `PooledConnection` wrapper that intercepts `close()` calls and returns the connection to the pool instead of actually closing it. This makes pooling transparent to application code.

**Files to Fix**:
- `OrderService.java` - `getOrderItemsWithValidation()` method (around line 155)
- `OrderService.java` - `getShippingInfoDetailed()` method (around line 215)

**How to Fix**:
1. **Option A**: Add `conn.close()` at the end of each buggy method
2. **Option B** (BEST): Refactor to use try-with-resources like the working methods
3. **Option C**: Delete the buggy methods and use only the correct ones

---

### Problem 1: N+1 Query Problem (Dormant, Emerges After Problem 2 Fixed) 🟡
**Location**: `OrderService.getCustomerOrders()`

**Why It's Hidden**: Connection leak limits throughput to ~50 requests before failure, so N+1 queries don't process enough volume to become noticeable.

**When It Emerges**: After fixing connection leak, throughput increases 10x. Now the N+1 queries become the bottleneck.

**Symptoms** (after Problem 2 fixed):
- Response times jump from 100ms → 5+ seconds
- Database query count explodes (151 queries per request for 50 orders!)
- Database CPU spikes
- Slow request alerts in Dynatrace

**The N+1 Problem**:
```java
// 1 query for orders
SELECT * FROM orders WHERE customer_id = ?

// N queries for items (once per order - 50 times!)
for each order:
    SELECT * FROM order_items WHERE order_id = ?
    
// N queries for shipping (once per order - 50 times!)
for each order:
    SELECT * FROM shipping WHERE order_id = ?

Total: 1 + 50 + 50 = 101 database round trips!
```

**How to Detect in Dynatrace**:
- High database query count per request
- Sequential database calls in PurePath
- Response time correlates with order count
- Database service load increases

**Fix**: Use JOIN queries to fetch all data in 1-3 queries instead of 100+
```sql
-- Single optimized query with JOINs
SELECT o.*, oi.*, s.*
FROM orders o
LEFT JOIN order_items oi ON o.order_id = oi.order_id
LEFT JOIN shipping s ON o.order_id = s.order_id
WHERE o.customer_id = ?
```

## Running the Demo

### Option 1: Launch Both Together (Recommended)
1. Press `F5` or go to Run & Debug
2. Select **"Server + Load Generator"** compound
3. Both server and load generator start automatically

### Option 2: Launch Separately
1. Start the HTTP server:
   - Select **"Start HTTP Server"**
   - Press `F5`
   - Wait for "OrderService HTTP Server started on port 8080"

2. Start the load generator (in another debug session):
   - Select **"Start Load Generator"**
   - Press `F5`
   - Generates HTTP load for 5 minutes (300 seconds)

## Workshop Flow

### Phase 1: Initial Problem (Connection Leak)
1. Start server + load generator
2. Watch Dynatrace detect the service automatically
3. After ~50 requests, observe errors and failures
4. Dynatrace creates Problem ticket: "Resource exhaustion on OrderService"
5. Use n8n workflow to:
   - Fetch problem details from Dynatrace API
   - Post to Slack channel
   - Create Jira ticket
   - Assign to GitHub Copilot for investigation

### Phase 2: Fix Connection Leak
1. GitHub Copilot analyzes code
2. Creates PR with try-with-resources fix
3. PR reviewed and merged
4. Redeploy application
5. Verify problem resolved in Dynatrace
6. Problem ticket auto-closes

### Phase 3: Second Problem Emerges (N+1 Queries)
1. After connection leak fixed, throughput increases
2. Load generator now processes 500+ requests successfully
3. Response times spike to 5+ seconds
4. Dynatrace detects new problem: "Response time degradation"
5. Repeat workflow with n8n/GitHub/Jira
6. Fix N+1 queries with JOIN statements
7. Verify resolution: response times drop to <100ms

## Test Data
- **Customer 1**: 50 orders (demonstrates N+1 problem well)
- **Customer 2**: 5 orders
- Each order has 2-3 items and shipping info
- Total: 55 orders, ~160 order items, 55 shipping records

## Configuration
All environment variables are pre-configured in `.vscode/launch.json`:
- PostgreSQL connection (auto-falls back to H2 if unavailable)
- No manual configuration needed

## Monitoring Expectations

### Dynatrace Will Automatically Detect:
✅ HTTP service on port 8080
✅ Service name: "OrderService"  
✅ Request response times
✅ Error rates
✅ Database calls and performance
✅ Connection pool metrics
✅ PurePath traces showing call hierarchy

### Problems Dynatrace Will Create:
1. **Resource exhaustion** (Problem 2) - ~1 minute after start
2. **Response time degradation** (Problem 1) - After Problem 2 fixed

## Integration Points

### Dynatrace API
- Problem feed: `GET /api/v2/problems`
- Metrics: `GET /api/v2/metrics/query`
- Events: `POST /api/v2/events`

### GitHub Integration
- Automated PR creation via Copilot
- Code analysis and suggestions
- PR review and merge

### Slack Notifications
- Problem alerts
- Resolution confirmations
- Status updates

### Jira Ticketing
- Auto-create tickets from problems
- Link to Dynatrace problem
- Track resolution

## Troubleshooting

**Server won't start**: Port 8080 already in use
- Solution: Stop other services on port 8080 or change PORT in `OrderServiceHttpServer.java`

**Load generator can't connect**: 
- Solution: Make sure HTTP server is running first

**No problems in Dynatrace**:
- Check OneAgent is running and in FullStack mode
- Wait 2-3 minutes for data to appear
- Verify process is running long enough (>1 minute)

**Database errors**:
- H2 driver missing: Check `lib/h2-2.2.224.jar` exists
- Should auto-initialize on startup

## Clean Slate
To reset and start fresh:
1. Stop both server and load generator
2. Restart - H2 database recreates automatically
3. Previous problems in Dynatrace will auto-close when service is healthy
