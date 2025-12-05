# PayMongo Integration Setup Guide

This guide explains how to set up PayMongo for Maya and GCash payment methods in the Food Ordering System.

## Overview

PayMongo is now integrated for processing GCash and Maya payments during checkout. The integration includes:

- **PayMongoConfig.java**: Configuration file for API keys
- **PayMongoService.java**: Service for handling PayMongo API calls
- **CheckoutActivity.java**: Updated to use PayMongo for GCash and Maya payments

## Setup Instructions

### 1. Get PayMongo API Keys

**Important:** You need **API Keys**, not the Merchant ID. The Merchant ID (like `org_xxx`) is different from API Keys.

1. Sign up or log in to [PayMongo Dashboard](https://dashboard.paymongo.com/)
2. In the left sidebar, click on **"Developers"**
3. Then click on **"API Keys"** (or navigate to **Developers > API Keys**)
4. You'll see two types of keys:
   - **Test Public Key** (starts with `pk_test_`) - Use this for `PAYMONGO_PUBLIC_KEY`
   - **Test Secret Key** (starts with `sk_test_`) - Use this for `PAYMONGO_SECRET_KEY`
5. Copy both keys - you'll need them in the next step

**Note:** Make sure you're using **Test Keys** (for development/demo), not Live Keys (for production).

### 2. Configure API Keys

Open `app/src/main/java/com/fp/foodorderingsystem/config/PayMongoConfig.java` and replace the placeholder values:

```java
// Replace this:
public static final String PAYMONGO_PUBLIC_KEY = "pk_test_YOUR_PUBLIC_KEY_HERE";
public static final String PAYMONGO_SECRET_KEY = "sk_test_YOUR_SECRET_KEY_HERE";

// With your actual keys:
public static final String PAYMONGO_PUBLIC_KEY = "pk_test_your_actual_key_here";
public static final String PAYMONGO_SECRET_KEY = "sk_test_your_actual_key_here";
```

### 3. Testing

1. Build and run the app
2. Add items to cart and proceed to checkout
3. Select **GCash** or **Maya** as payment method
4. Click **Place Order**
5. The app will:
   - Create a PayMongo payment intent
   - Open the PayMongo checkout URL in the browser
   - Allow you to complete payment
   - Return to the app and check payment status

### 4. Demo Mode

If PayMongo API keys are not configured, the app will fall back to **simulated payment** mode for demo purposes. You'll see a dialog offering to simulate the payment.

## Payment Flow

1. **User selects payment method** (GCash or Maya)
2. **User clicks "Place Order"**
3. **App creates PayMongo payment intent** with order amount
4. **PayMongo returns checkout URL**
5. **App opens checkout URL** in browser
6. **User completes payment** on PayMongo
7. **User returns to app**
8. **App checks payment status** automatically
9. **If payment successful**, order is created

## Features

- ✅ GCash payment integration
- ✅ Maya payment integration  
- ✅ Payment status checking
- ✅ Automatic retry for processing payments
- ✅ Fallback to simulated payment for demo
- ✅ Error handling and user feedback

## Important Notes

⚠️ **Security Warning**: 
- For production, **never** store secret keys in the Android app
- Use a backend server to handle PayMongo secret key operations
- The current implementation stores secret key in the app **for demo purposes only**

⚠️ **Test Mode**:
- Use test keys (`pk_test_` and `sk_test_`) for development
- Switch to live keys (`pk_live_` and `sk_live_`) only in production
- Test payments won't charge real money

## Troubleshooting

### Payment intent creation fails
- Check if API keys are correctly configured
- Verify internet connection
- Check PayMongo dashboard for API errors

### Payment status check fails
- Ensure the payment intent ID is valid
- Check network connectivity
- Manually verify payment on PayMongo dashboard

### Checkout URL doesn't open
- Ensure browser is installed on device
- Check if URL is valid
- Verify PayMongo API response

## Support

For PayMongo API documentation, visit: https://developers.paymongo.com/

For issues with this integration, check:
- PayMongo service logs (tag: "PayMongo")
- CheckoutActivity logs (tag: "CheckoutActivity")

