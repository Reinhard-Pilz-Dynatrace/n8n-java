# Quick Fix Reference

## Problem 2: Connection Leak - Fix Guide

### Understanding the Bug

The code has **two similar methods** for fetching order items:
1. `getOrderItemsStandard()` - ✅ Properly uses try-with-resources
2. `getOrderItemsWithValidation()` - ❌ Forgets to close connection

The router method `getOrderItems()` calls the buggy method 20% of the time, causing intermittent leaks.

### Current Code (BROKEN - leaks 20% of connections)

**File**: `OrderService.java`  
**Method**: `getOrderItemsWithValidation()` (around line 155)

```java
/**
 * "Enhanced" code path with validation (someone's "improvement" that introduced a bug)
 * PROBLEM: Forgets to close connection!
 */
private List<OrderItem> getOrderItemsWithValidation(int orderId) throws SQLException {
    List<OrderItem> items = new ArrayList<>();
    
    // PROBLEM: Connection not wrapped in try-with-resources!
    Connection conn = connectionPool.getConnection();
    PreparedStatement stmt = conn.prepareStatement(
        "SELECT item_id, product_name, quantity, price FROM order_items WHERE order_id = ?"
    );
    stmt.setInt(1, orderId);
    ResultSet rs = stmt.executeQuery();
    
    while (rs.next()) {
        OrderItem item = new OrderItem();
        item.setItemId(rs.getInt("item_id"));
        item.setProductName(rs.getString("product_name"));
        item.setQuantity(rs.getInt("quantity"));
        item.setPrice(rs.getDouble("price"));
        
        // "Validation logic" - checking for valid data
        if (item.getQuantity() > 0 && item.getPrice() > 0) {
            items.add(item);
        }
    }
    
    rs.close();
    stmt.close();
    // BUG: Forgot to close connection! Copy-paste error?
    // Developer probably got distracted after adding validation logic
    
    return items;
}
```

### Fixed Code (Option 1: Add the missing conn.close())

```java
private List<OrderItem> getOrderItemsWithValidation(int orderId) throws SQLException {
    List<OrderItem> items = new ArrayList<>();
    
    Connection conn = connectionPool.getConnection();
    PreparedStatement stmt = conn.prepareStatement(
        "SELECT item_id, product_name, quantity, price FROM order_items WHERE order_id = ?"
    );
    stmt.setInt(1, orderId);
    ResultSet rs = stmt.executeQuery();
    
    while (rs.next()) {
        OrderItem item = new OrderItem();
        item.setItemId(rs.getInt("item_id"));
        item.setProductName(rs.getString("product_name"));
        item.setQuantity(rs.getInt("quantity"));
        item.setPrice(rs.getDouble("price"));
        
        if (item.getQuantity() > 0 && item.getPrice() > 0) {
            items.add(item);
        }
    }
    
    rs.close();
    stmt.close();
    conn.close(); // FIX: Add this line to return connection to pool
    
    return items;
}
```

### Fixed Code (Option 2: Use try-with-resources - BEST PRACTICE)

```java
private List<OrderItem> getOrderItemsWithValidation(int orderId) throws SQLException {
    List<OrderItem> items = new ArrayList<>();
    
    // Wrap in try-with-resources to guarantee close() is called
    try (Connection conn = connectionPool.getConnection();
         PreparedStatement stmt = conn.prepareStatement(
             "SELECT item_id, product_name, quantity, price FROM order_items WHERE order_id = ?")) {
        
        stmt.setInt(1, orderId);
        
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                OrderItem item = new OrderItem();
                item.setItemId(rs.getInt("item_id"));
                item.setProductName(rs.getString("product_name"));
                item.setQuantity(rs.getInt("quantity"));
                item.setPrice(rs.getDouble("price"));
                
                if (item.getQuantity() > 0 && item.getPrice() > 0) {
                    items.add(item);
                }
            }
        }
        
        return items;
    } // Automatically closes all resources, even if exception occurs!
}
```

### Fixed Code (Option 3: Eliminate Code Duplication - BEST ARCHITECTURAL FIX)

The best fix is to **delete the buggy method** and use only the correct one:

```java
private List<OrderItem> getOrderItems(int orderId) throws SQLException {
    // Just use the correct implementation - no need for two methods!
    return getOrderItemsStandard(orderId);
}
```

Then delete `getOrderItemsWithValidation()` entirely. The "validation" wasn't necessary anyway since the database constraints already ensure positive values.

---

## Same Fix Needed for Shipping Info

**File**: `OrderService.java`  
**Method**: `getShippingInfoDetailed()` (around line 215)

Apply the same fix - add `conn.close()` or refactor to try-with-resources:

```java
// Quick fix: Add conn.close() before return
rs.close();
stmt.close();
conn.close(); // Add this line!

return info;
```

Or better yet, use try-with-resources like `getShippingInfoFast()` does

---

## Why Try-With-Resources is Better

### Manual close() - can forget or miss error paths:
```java
Connection conn = pool.getConnection();
PreparedStatement stmt = conn.prepareStatement(...);
ResultSet rs = stmt.executeQuery();

// Process data...

rs.close();
stmt.close();
conn.close(); // What if exception thrown before this?
```

### Try-with-resources - guaranteed cleanup:
```java
try (Connection conn = pool.getConnection();
     PreparedStatement stmt = conn.prepareStatement(...);
     ResultSet rs = stmt.executeQuery()) {
    
    // Process data...
    // If exception thrown here, all resources still close!
    
} // Automatic cleanup in reverse order: rs, stmt, conn
```

**Benefits**:
- ✅ Guaranteed to close even if exception thrown
- ✅ Closes in correct order (reverse of creation)
- ✅ Cleaner code - no finally blocks needed
- ✅ Less error-prone - can't forget to close
- ✅ Works with any AutoCloseable resource

---

## How the Connection Pool Works

The `SimpleConnectionPool` uses a wrapper pattern:

1. **PooledConnection** wraps the real JDBC Connection
2. When you call `conn.close()`, it **doesn't actually close**
3. Instead, it returns the connection to the pool for reuse
4. The wrapper has `reallyClose()` for actual cleanup on pool shutdown

```java
// PooledConnection.close() implementation:
@Override
public void close() throws SQLException {
    if (!closed) {
        closed = true;
        pool.returnConnection(this); // Returns to pool, not actually closed!
    }
}
```

This makes connection pooling transparent to your application code - just call `close()` normally!

---

## Testing Your Fix

1. **Before Fix**: 
   - Start the app
   - Watch errors appear after ~50-60 requests
   - Pool exhausts in 1-2 minutes

2. **After Fix**:
   - Start the app
   - Should run indefinitely without pool exhaustion
   - Response times consistent
   - Then N+1 query problem becomes visible!

3. **In Dynatrace**:
   - Problem ticket should auto-close
   - Database connection metrics return to normal
   - Error rate drops to 0%
