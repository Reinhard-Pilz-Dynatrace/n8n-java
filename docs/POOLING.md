# Connection Pooling Architecture

## How Transparent Connection Pooling Works

This workshop application uses a **transparent connection pool** - connection pooling that's invisible to the application code. You just call `close()` like normal, and the connection is automatically returned to the pool.

## The Components

### 1. SimpleConnectionPool (The Pool Manager)

**Responsibilities**:
- Maintains a queue of available connections
- Creates new connections up to MAX_CONNECTIONS (10)
- Waits for available connections when pool is exhausted
- Throws exception if timeout expires (5 seconds)

**Key Settings**:
```java
MAX_CONNECTIONS = 10  // Small limit to make leaks obvious
CONNECTION_TIMEOUT_SECONDS = 5  // Quick timeout for demo
```

### 2. PooledConnection (The Wrapper)

**Responsibilities**:
- Wraps a real JDBC Connection
- Intercepts `close()` calls
- Returns connection to pool instead of actually closing
- Delegates all other methods to the real connection

**Key Methods**:
```java
close()       // Returns to pool (transparent to application)
reallyClose() // Actually closes the real connection (for pool shutdown)
reopen()      // Marks wrapper as active when retrieved from pool
```

## The Magic: close() Interception

### What Normal JDBC Does
```java
Connection conn = DriverManager.getConnection(...);
// use connection
conn.close(); // ← Closes the TCP socket, destroys the connection
```

### What Our Pool Does
```java
Connection conn = pool.getConnection(); // Returns PooledConnection wrapper
// use connection
conn.close(); // ← Calls pool.returnConnection(this), connection stays alive!
```

## Code Flow Diagram

```
Application Code
    ↓
    getConnection()
    ↓
SimpleConnectionPool
    ↓
    [Has available connection?]
    ├─ Yes: Return existing PooledConnection (call reopen())
    ├─ No, but < MAX: Create new Connection → Wrap in PooledConnection
    └─ No, at MAX: Wait for return or timeout
    ↓
PooledConnection wrapper
    ↓ (returned to application)
Application uses connection
    ↓
    close() called
    ↓
PooledConnection.close()
    ↓
    [Doesn't actually close!]
    ↓
pool.returnConnection(this)
    ↓
SimpleConnectionPool
    ↓
    [Add back to queue]
    ↓
    [Connection available for reuse]
```

## The Leak Scenario

### When You Forget to Close

```java
Connection conn = pool.getConnection();
// use connection
return data; // ← LEAK! Never closed
```

**What happens**:
1. Connection taken from pool
2. Application uses it
3. Application returns/exits without calling close()
4. Connection stays "checked out" forever
5. Pool shrinks by 1 available connection
6. Repeat 10 times → pool exhausted!

### With Our Intermittent Leak (20%)

```java
if (Math.random() > 0.20) {
    conn.close(); // 80% of time: return to pool
}
// 20% of time: leak it
```

**Timeline**:
- 50 requests = 100 connections needed (2 per request: items + shipping)
- 20% leak = 20 connections leaked
- But we already leaked from pool of 10...
- After ~50 requests, pool is exhausted
- Takes 1-2 minutes with 20 threads at 50ms think time

## Why This Makes a Good Demo

### Realistic
- Mimics real-world bugs where developers forget to close resources
- Gradual degradation (not immediate failure)
- Error messages clearly indicate resource exhaustion

### Educational
- Shows importance of try-with-resources
- Demonstrates connection pool concepts
- Clear cause and effect relationship

### Dynatrace-Friendly
- Dynatrace easily detects the problem:
  - High database connection count
  - Connection pool exhaustion errors
  - Response time degradation
  - Service failures

## The Fix is Simple

### Bad Code (what we have)
```java
Connection conn = pool.getConnection();
// use it
if (Math.random() > 0.20) { conn.close(); } // Sometimes forget!
```

### Good Code (the fix)
```java
try (Connection conn = pool.getConnection()) {
    // use it
} // ALWAYS closes, no matter what!
```

## Implementation Details

### PooledConnection.java (Simplified)
```java
public class PooledConnection implements Connection {
    private final Connection realConnection;
    private final SimpleConnectionPool pool;
    private boolean closed = false;
    
    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            pool.returnConnection(this); // Don't actually close!
        }
    }
    
    public void reallyClose() throws SQLException {
        realConnection.close(); // Actually close the real connection
    }
    
    public void reopen() {
        closed = false; // Mark as active again
    }
    
    // All other methods delegate to realConnection:
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return realConnection.prepareStatement(sql);
    }
    // ... 40+ other Connection methods ...
}
```

### SimpleConnectionPool.java (Simplified)
```java
public class SimpleConnectionPool {
    private BlockingQueue<PooledConnection> availableConnections;
    
    public Connection getConnection() throws SQLException {
        PooledConnection conn = availableConnections.poll();
        
        if (conn == null && totalCreated < MAX_CONNECTIONS) {
            // Create new
            Connection real = DriverManager.getConnection(...);
            conn = new PooledConnection(real, this);
            totalCreated++;
        }
        
        if (conn == null) {
            // Wait for return
            conn = availableConnections.poll(5, TimeUnit.SECONDS);
            if (conn == null) throw new SQLException("Pool exhausted!");
        }
        
        conn.reopen(); // Mark as active
        return conn;
    }
    
    void returnConnection(PooledConnection conn) {
        availableConnections.offer(conn); // Add back to queue
    }
}
```

## Testing the Pool

### Verify Pool Exhaustion (Before Fix)
1. Start WorkshopDemo
2. Watch console for "Created new connection. Total: X/10"
3. After ~50 requests, see "⚠ WARNING: Connection pool exhausted!"
4. Requests start failing with timeout exceptions

### Verify Pool Works (After Fix)
1. Apply connection leak fix (always close)
2. Start WorkshopDemo
3. Pool stays at 10 connections
4. All connections get reused
5. No pool exhaustion errors
6. Service runs indefinitely

### Monitor in Dynatrace
- Database connection metrics
- Error rates
- Response times
- Resource utilization

## Common Patterns in Real Applications

### Pattern 1: Connection Pooling Libraries
- HikariCP
- Apache DBCP
- C3P0
- Tomcat JDBC Pool

All use similar wrapper patterns!

### Pattern 2: Application Servers
- Tomcat provides DataSource pooling
- JBoss/WildFly provide connection pools
- Spring Boot auto-configures HikariCP

### Pattern 3: Cloud Services
- AWS RDS Proxy provides connection pooling
- Azure SQL connection pooling
- Cloud providers handle at infrastructure level

## Key Takeaways

1. **Connection pooling is essential** for database performance
2. **Always use try-with-resources** for JDBC resources
3. **Pool exhaustion** is a common production issue
4. **Monitoring is critical** to detect resource leaks
5. **Transparent pooling** makes it easier to use correctly

The wrapper pattern shown here is used in production-grade connection pools like HikariCP!
