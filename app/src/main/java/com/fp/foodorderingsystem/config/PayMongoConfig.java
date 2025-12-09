package com.fp.foodorderingsystem.config;

/**
 * PayMongo Configuration
 * Contains API keys and URLs for connecting to PayMongo payment gateway
 * 
 * For demo purposes, use PayMongo test keys:
 * - Test Public Key: pk_test_...
 * - Test Secret Key: sk_test_...
 * 
 * Get your keys from: https://dashboard.paymongo.com/
 */
public class PayMongoConfig {
    
    // PayMongo API Base URL
    public static final String PAYMONGO_API_URL = "https://api.paymongo.com/v1";
    
    // PayMongo Public Key (used for client-side operations)
    // Injected via BuildConfig; configure in local.properties as PAYMONGO_PUBLIC_KEY
    public static final String PAYMONGO_PUBLIC_KEY = BuildConfig.PAYMONGO_PUBLIC_KEY;
    
    // PayMongo Secret Key (used for server-side operations)
    // Injected via BuildConfig; configure in local.properties as PAYMONGO_SECRET_KEY
    public static final String PAYMONGO_SECRET_KEY = BuildConfig.PAYMONGO_SECRET_KEY;
    
    // Payment method types supported
    public static final String PAYMENT_METHOD_GCASH = "gcash";
    public static final String PAYMENT_METHOD_MAYA = "paymaya";
    
    // Payment intent statuses
    public static final String STATUS_AWAITING_PAYMENT_METHOD = "awaiting_payment_method";
    public static final String STATUS_AWAITING_NEXT_ACTION = "awaiting_next_action";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_CANCELED = "canceled";
    
    // Return URL for PayMongo redirects
    // Must be HTTPS. We use a unique domain path and intercept it inside our in-app WebView.
    public static final String PAYMENT_RETURN_URL = "https://foodorderingsystem.app/payment-return";
    
    // Deep link scheme for app (used internally if needed)
    public static final String APP_DEEP_LINK_SCHEME = "foodorderingsystem://payment-return";
}

