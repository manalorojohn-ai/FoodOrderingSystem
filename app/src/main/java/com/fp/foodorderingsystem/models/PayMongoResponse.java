package com.fp.foodorderingsystem.models;

import com.google.gson.JsonObject;

/**
 * PayMongo API Response wrapper
 */
public class PayMongoResponse {
    private PayMongoData data;
    private JsonObject errors;
    
    public PayMongoResponse() {}
    
    public PayMongoData getData() { return data; }
    public void setData(PayMongoData data) { this.data = data; }
    
    public JsonObject getErrors() { return errors; }
    public void setErrors(JsonObject errors) { this.errors = errors; }
    
    public boolean hasErrors() {
        return errors != null;
    }
    
    public static class PayMongoData {
        private String id;
        private String type;
        private JsonObject attributes;
        
        public PayMongoData() {}
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public JsonObject getAttributes() { return attributes; }
        public void setAttributes(JsonObject attributes) { this.attributes = attributes; }
    }
}

