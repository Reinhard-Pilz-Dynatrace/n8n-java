package com.dynatrace.ecommerce;

import java.util.List;

public class Order {
    private int orderId;
    private java.util.Date orderDate;
    private double totalAmount;
    private List<OrderItem> items;
    private ShippingInfo shippingInfo;
    
    // Getters and setters
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public java.util.Date getOrderDate() { return orderDate; }
    public void setOrderDate(java.util.Date orderDate) { this.orderDate = orderDate; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public ShippingInfo getShippingInfo() { return shippingInfo; }
    public void setShippingInfo(ShippingInfo shippingInfo) { this.shippingInfo = shippingInfo; }
}
