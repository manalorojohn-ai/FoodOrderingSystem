package com.fp.foodorderingsystem.models;

/**
 * PayMongo Payment Intent model
 * Represents a payment intent in PayMongo
 */
public class PayMongoPaymentIntent {
    private String id;
    private String type;
    private PayMongoAttributes attributes;
    
    public PayMongoPaymentIntent() {}
    
    public static class PayMongoAttributes {
        private int amount;
        private String currency;
        private String status;
        private String paymentMethodAllocation;
        private String description;
        private PayMongoNextAction nextAction;
        
        public PayMongoAttributes() {}
        
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getPaymentMethodAllocation() { return paymentMethodAllocation; }
        public void setPaymentMethodAllocation(String paymentMethodAllocation) { 
            this.paymentMethodAllocation = paymentMethodAllocation; 
        }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public PayMongoNextAction getNextAction() { return nextAction; }
        public void setNextAction(PayMongoNextAction nextAction) { this.nextAction = nextAction; }
    }
    
    public static class PayMongoNextAction {
        private String type;
        private String url;
        
        public PayMongoNextAction() {}
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public PayMongoAttributes getAttributes() { return attributes; }
    public void setAttributes(PayMongoAttributes attributes) { this.attributes = attributes; }
}

