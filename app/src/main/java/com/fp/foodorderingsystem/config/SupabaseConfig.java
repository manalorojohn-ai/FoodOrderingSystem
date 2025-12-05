package com.fp.foodorderingsystem.config;

/**
 * Supabase Configuration
 * Contains API keys and URLs for connecting to Supabase backend
 */
public class SupabaseConfig {
    
    // Your Supabase Project URL
    public static final String SUPABASE_URL = "https://wnsebtlndonfskwbhjfb.supabase.co";
    
    // Your Supabase Anon/Public Key (safe to use in client-side code)
    public static final String SUPABASE_ANON_KEY = "sb_publishable_k_PgtKrLsR4yqHZ2ArCk2Q_4838Ghkx";
    
    // Supabase Realtime WebSocket endpoint
    public static final String SUPABASE_REALTIME_URL = SUPABASE_URL
        .replaceFirst("https://", "wss://")
        + "/realtime/v1/websocket";
    
    // DO NOT USE service_role key in client-side code! 
    // It's only for server-side operations and has full admin access
    // private static final String SUPABASE_SERVICE_KEY = "eyJhbGc..."; // KEEP THIS SECRET!
}
