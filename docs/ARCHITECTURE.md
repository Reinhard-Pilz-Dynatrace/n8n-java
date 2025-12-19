# Code Architecture - Connection Leak Pattern

## The Two-Path Pattern (More Realistic)

This demonstrates a **realistic bug pattern** where code duplication leads to inconsistent resource management.

```
HTTP Request → OrderService.getCustomerOrders()
                    ↓
                For each order:
                    ↓
            ┌───────────────────┐
            │  getOrderItems()  │ ← Router method
            └─────────┬─────────┘
                      │
        ┌─────────────┴─────────────┐
        │                           │
   80% Random                  20% Random
        │                           │
        ▼                           ▼
┌──────────────────┐      ┌─────────────────────────┐
│ Standard Path    │      │ Validation Path         │
│ ✅ WORKS FINE    │      │ ❌ CONNECTION LEAK      │
├──────────────────┤      ├─────────────────────────┤
│ try-with-res     │      │ Manual management       │
│ conn.close() ✓   │      │ conn.close() MISSING!   │
└──────────────────┘      └─────────────────────────┘
        │                           │
        └─────────────┬─────────────┘
                      │
                Returns List<OrderItem>
                      │
            ┌─────────▼─────────┐
            │ getShippingInfo() │ ← Another router
            └─────────┬─────────┘
                      │
        ┌─────────────┴─────────────┐
        │                           │
   80% Random                  20% Random
        │                           │
        ▼                           ▼
┌──────────────────┐      ┌─────────────────────────┐
│ Fast Path        │      │ Detailed Path           │
│ ✅ WORKS FINE    │      │ ❌ CONNECTION LEAK      │
├──────────────────┤      ├─────────────────────────┤
│ try-with-res     │      │ Manual + "enhancement"  │
│ conn.close() ✓   │      │ conn.close() MISSING!   │
└──────────────────┘      └─────────────────────────┘
        │                           │
        └─────────────┬─────────────┘
                      │
              Returns ShippingInfo
```

## Why This Pattern is Realistic

### Common Real-World Scenarios:

1. **"Optimization" Gone Wrong**
   - Developer creates "optimized" path for special cases
   - Copies working code but introduces bug
   - Less frequently used path → bug takes time to surface

2. **Code Duplication**
   - Two similar methods with subtle differences
   - One uses try-with-resources, other uses manual management
   - Inconsistent patterns lead to resource leaks

3. **Copy-Paste Errors**
   - Developer copies method to add feature
   - Gets distracted by new feature implementation
   - Forgets to close resources in the new code

4. **"Enhancement" Bug**
   - Original code works fine
   - Someone adds "validation" or "formatting"
   - Breaks resource management in the process

## The Methods

### Order Items

#### ✅ getOrderItemsStandard()
```java
private List<OrderItem> getOrderItemsStandard(int orderId) throws SQLException {
    // GOOD: Uses try-with-resources
    try (Connection conn = pool.getConnection();
         PreparedStatement stmt = conn.prepareStatement(...)) {
        // ... process results ...
        return items;
    } // Connection automatically closed
}
```

#### ❌ getOrderItemsWithValidation()
```java
private List<OrderItem> getOrderItemsWithValidation(int orderId) throws SQLException {
    // BAD: Manual resource management
    Connection conn = pool.getConnection();
    PreparedStatement stmt = conn.prepareStatement(...);
    ResultSet rs = stmt.executeQuery();
    
    // ... process with validation ...
    
    rs.close();
    stmt.close();
    // BUG: Forgot conn.close()!
    
    return items;
}
```

### Shipping Info

#### ✅ getShippingInfoFast()
```java
private ShippingInfo getShippingInfoFast(int orderId) throws SQLException {
    // GOOD: Uses try-with-resources
    try (Connection conn = pool.getConnection();
         PreparedStatement stmt = conn.prepareStatement(...)) {
        // ... process results ...
        return info;
    } // Connection automatically closed
}
```

#### ❌ getShippingInfoDetailed()
```java
private ShippingInfo getShippingInfoDetailed(int orderId) throws SQLException {
    // BAD: Manual resource management
    Connection conn = pool.getConnection();
    PreparedStatement stmt = conn.prepareStatement(...);
    ResultSet rs = stmt.executeQuery();
    
    // ... process with "enhancement" ...
    
    rs.close();
    stmt.close();
    // BUG: Forgot conn.close()!
    
    return info;
}
```

## The Impact

### Timeline
```
Time    Requests  Leaks  Available  Status
---------------------------------------------
0:00         0      0      10/10    ✅ Healthy
0:15        25      5       5/10    ⚠️ Degrading
0:30        50     10       0/10    🔴 Exhausted!
0:31        51     10       0/10    💥 Errors!
```

### Per Request
- Each request processes 1 order with items + shipping
- 2 database operations per request
- 20% of each operation leaks (2 × 0.20 = 0.4 leaks per request)
- After ~25 requests: half the pool leaked
- After ~50 requests: pool exhausted

## How Dynatrace Detects This

### Symptoms Visible in Dynatrace:
1. **Database Connection Metrics**
   - Connection count steadily increases
   - Available connections decrease
   - Connection wait time increases

2. **Error Patterns**
   - "Connection pool exhausted" errors appear
   - Errors increase over time (gradual)
   - Error rate: starts at 0%, reaches 100%

3. **Response Time**
   - First 40 requests: normal (~100ms)
   - Next 10 requests: degrading (500ms-2s)
   - After exhaustion: timeouts (5s+)

4. **PurePath Analysis**
   - Shows which code paths are leaking
   - Identifies missing resource cleanup
   - Traces connections not returned to pool

## The Fix Strategy

### Quick Fix (Band-Aid)
Add `conn.close()` to both buggy methods:
```java
rs.close();
stmt.close();
conn.close(); // Add this line
```

### Better Fix (Refactor)
Convert to try-with-resources:
```java
try (Connection conn = pool.getConnection();
     PreparedStatement stmt = ...) {
    // ... code ...
}
```

### Best Fix (Architectural)
Eliminate code duplication:
```java
private List<OrderItem> getOrderItems(int orderId) {
    return getOrderItemsStandard(orderId); // Just use the good one!
}
// Delete the buggy method entirely
```

## Workshop Learning Points

### For Attendees:
1. **Code duplication is dangerous** - leads to inconsistent patterns
2. **Try-with-resources is essential** - prevents these bugs
3. **Intermittent bugs are hardest to find** - gradual degradation
4. **Monitoring is critical** - Dynatrace catches what testing misses
5. **Architectural debt matters** - duplicate code = duplicate bugs

### For Presenters:
1. More realistic than obvious `if (Math.random())` in business logic
2. Shows how "enhancements" can introduce bugs
3. Demonstrates importance of code reviews
4. Highlights value of automated monitoring
5. Good example for discussing refactoring vs quick fixes

## Code Review Checklist

When reviewing JDBC code, always check:
- ✅ Using try-with-resources for Connection?
- ✅ Using try-with-resources for Statement?
- ✅ Using try-with-resources for ResultSet?
- ✅ No duplicate code paths with different resource management?
- ✅ All code paths close resources properly?
- ✅ Exception paths also close resources?

If any ❌, you have a potential resource leak!
