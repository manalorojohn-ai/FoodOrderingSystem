package com.fp.foodorderingsystem.services;

import android.util.Log;
import com.fp.foodorderingsystem.models.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RealtimeUserManager provides real-time user data synchronization with automatic
 * updates, filtering, and offline support. Handles all user-related realtime events
 * from Supabase and maintains a local cache of users.
 */
public class RealtimeUserManager {
    private static final String TAG = "RealtimeUserManager";
    
    public interface UserListListener {
        void onUsersUpdated(List<User> users);
        void onConnectionStateChanged(boolean connected);
        void onError(String error);
    }
    
    public interface UserEventListener {
        void onUserAdded(User user);
        void onUserUpdated(User user);
        void onUserDeleted(String userId);
        void onConnectionStateChanged(boolean connected);
        void onError(String error);
    }
    
    private final UserService userService;
    private final SupabaseRealtimeClient realtimeClient;
    private final Gson gson;
    private final List<User> cachedUsers;
    private final CopyOnWriteArrayList<UserListListener> listListeners;
    private final CopyOnWriteArrayList<UserEventListener> eventListeners;
    private final AtomicBoolean isConnected;
    private final Object syncLock = new Object();
    
    private String currentSearchQuery = "";
    private UserService.UserListCallback pendingCallback;
    
    public RealtimeUserManager(UserService userService) {
        this.userService = userService;
        this.realtimeClient = new SupabaseRealtimeClient();
        this.gson = new Gson();
        this.cachedUsers = Collections.synchronizedList(new ArrayList<>());
        this.listListeners = new CopyOnWriteArrayList<>();
        this.eventListeners = new CopyOnWriteArrayList<>();
        this.isConnected = new AtomicBoolean(false);
    }
    
    /**
     * Start real-time synchronization for users
     */
    public void start(String accessToken) {
        // Load initial data
        userService.getAllUsers(accessToken, new UserService.UserListCallback() {
            @Override
            public void onSuccess(List<User> users) {
                synchronized (syncLock) {
                    cachedUsers.clear();
                    if (users != null) {
                        cachedUsers.addAll(users);
                        sortUsers();
                    }
                }
                notifyListenersUserUpdate();
            }
            
            @Override
            public void onError(String error) {
                notifyListenersError("Failed to load initial users: " + error);
            }
        });
        
        // Subscribe to realtime changes
        realtimeClient.subscribeToTable("public", "users", new SupabaseRealtimeClient.RealtimeListener() {
            @Override
            public void onOpen() {
                isConnected.set(true);
                Log.d(TAG, "Realtime connection established");
                notifyConnectionStateChanged(true);
            }
            
            @Override
            public void onChange(JsonObject payload) {
                handleRealtimeChange(payload);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Realtime connection error: " + error);
                notifyListenersError("Connection error: " + error);
            }
        });
    }
    
    /**
     * Get all cached users (filtered by current search query if set)
     */
    public List<User> getUsers() {
        synchronized (syncLock) {
            if (currentSearchQuery.isEmpty()) {
                return new ArrayList<>(cachedUsers);
            }
            return filterUsers(cachedUsers, currentSearchQuery);
        }
    }
    
    /**
     * Get user by ID
     */
    public User getUserById(String userId) {
        synchronized (syncLock) {
            for (User user : cachedUsers) {
                if (user.getId().equals(userId)) {
                    return user;
                }
            }
        }
        return null;
    }
    
    /**
     * Search and filter users
     */
    public List<User> searchUsers(String query) {
        currentSearchQuery = query != null ? query.trim() : "";
        synchronized (syncLock) {
            List<User> filtered = filterUsers(cachedUsers, currentSearchQuery);
            return filtered;
        }
    }
    
    /**
     * Add list update listener
     */
    public void addListener(UserListListener listener) {
        if (listener != null) {
            listListeners.add(listener);
        }
    }
    
    /**
     * Remove list update listener
     */
    public void removeListener(UserListListener listener) {
        listListeners.remove(listener);
    }
    
    /**
     * Add event listener for individual user changes
     */
    public void addEventListener(UserEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }
    
    /**
     * Remove event listener
     */
    public void removeEventListener(UserEventListener listener) {
        eventListeners.remove(listener);
    }
    
    /**
     * Check if realtime connection is active
     */
    public boolean isConnected() {
        return isConnected.get();
    }
    
    /**
     * Handle realtime changes from Supabase
     */
    private void handleRealtimeChange(JsonObject payload) {
        if (payload == null) {
            return;
        }
        
        String eventType = getEventType(payload);
        JsonObject newRecord = getNewRecord(payload);
        JsonObject oldRecord = getOldRecord(payload);
        
        synchronized (syncLock) {
            switch (eventType.toUpperCase()) {
                case "INSERT":
                    if (newRecord != null) {
                        try {
                            User newUser = gson.fromJson(newRecord, User.class);
                            if (newUser != null && !userExists(newUser.getId())) {
                                cachedUsers.add(0, newUser);
                                sortUsers();
                                Log.d(TAG, "User added via realtime: " + newUser.getId());
                                notifyEventListenerUserAdded(newUser);
                                notifyListenersUserUpdate();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing INSERT payload", e);
                        }
                    }
                    break;
                    
                case "UPDATE":
                    if (newRecord != null) {
                        try {
                            User updatedUser = gson.fromJson(newRecord, User.class);
                            if (updatedUser != null) {
                                upsertUser(updatedUser);
                                Log.d(TAG, "User updated via realtime: " + updatedUser.getId());
                                notifyEventListenerUserUpdated(updatedUser);
                                notifyListenersUserUpdate();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing UPDATE payload", e);
                        }
                    }
                    break;
                    
                case "DELETE":
                    if (oldRecord != null && oldRecord.has("id")) {
                        try {
                            String userId = oldRecord.get("id").getAsString();
                            removeUser(userId);
                            Log.d(TAG, "User deleted via realtime: " + userId);
                            notifyEventListenerUserDeleted(userId);
                            notifyListenersUserUpdate();
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing DELETE payload", e);
                        }
                    }
                    break;
            }
        }
    }
    
    /**
     * Internal: upsert user in cache
     */
    private void upsertUser(User user) {
        boolean found = false;
        for (int i = 0; i < cachedUsers.size(); i++) {
            if (cachedUsers.get(i).getId().equals(user.getId())) {
                cachedUsers.set(i, user);
                found = true;
                break;
            }
        }
        if (!found) {
            cachedUsers.add(user);
        }
        sortUsers();
    }
    
    /**
     * Internal: check if user exists
     */
    private boolean userExists(String userId) {
        for (User user : cachedUsers) {
            if (user.getId().equals(userId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Internal: remove user from cache
     */
    private void removeUser(String userId) {
        for (int i = 0; i < cachedUsers.size(); i++) {
            if (cachedUsers.get(i).getId().equals(userId)) {
                cachedUsers.remove(i);
                break;
            }
        }
    }
    
    /**
     * Internal: filter users by query
     */
    private List<User> filterUsers(List<User> users, String query) {
        if (query.isEmpty()) {
            return new ArrayList<>(users);
        }
        String lowerQuery = query.toLowerCase();
        List<User> filtered = new ArrayList<>();
        for (User user : users) {
            String name = user.getFullName() != null ? user.getFullName().toLowerCase() : "";
            String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";
            if (name.contains(lowerQuery) || email.contains(lowerQuery)) {
                filtered.add(user);
            }
        }
        return filtered;
    }
    
    /**
     * Internal: sort users by update time
     */
    private void sortUsers() {
        Collections.sort(cachedUsers, (a, b) -> {
            String updatedA = a != null ? a.getUpdatedAt() : null;
            String updatedB = b != null ? b.getUpdatedAt() : null;
            if (updatedA == null && updatedB == null) return 0;
            if (updatedA == null) return 1;
            if (updatedB == null) return -1;
            return updatedB.compareTo(updatedA);
        });
    }
    
    /**
     * Internal: extract event type from payload
     */
    private String getEventType(JsonObject payload) {
        if (payload.has("eventType")) {
            return payload.get("eventType").getAsString();
        } else if (payload.has("type")) {
            return payload.get("type").getAsString();
        }
        return "";
    }
    
    /**
     * Internal: extract new record from payload
     */
    private JsonObject getNewRecord(JsonObject payload) {
        if (payload.has("new")) {
            return payload.getAsJsonObject("new");
        } else if (payload.has("new_record")) {
            return payload.getAsJsonObject("new_record");
        }
        return null;
    }
    
    /**
     * Internal: extract old record from payload
     */
    private JsonObject getOldRecord(JsonObject payload) {
        if (payload.has("old")) {
            return payload.getAsJsonObject("old");
        } else if (payload.has("old_record")) {
            return payload.getAsJsonObject("old_record");
        }
        return null;
    }
    
    /**
     * Internal: notify list listeners of user update
     */
    private void notifyListenersUserUpdate() {
        List<User> users = getUsers();
        for (UserListListener listener : listListeners) {
            try {
                listener.onUsersUpdated(users);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }
    
    /**
     * Internal: notify listeners of error
     */
    private void notifyListenersError(String error) {
        for (UserListListener listener : listListeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of error", e);
            }
        }
    }
    
    /**
     * Internal: notify connection state change
     */
    private void notifyConnectionStateChanged(boolean connected) {
        for (UserListListener listener : listListeners) {
            try {
                listener.onConnectionStateChanged(connected);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of connection change", e);
            }
        }
        for (UserEventListener listener : eventListeners) {
            try {
                listener.onConnectionStateChanged(connected);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying event listener of connection change", e);
            }
        }
    }
    
    /**
     * Internal: notify event listener of user added
     */
    private void notifyEventListenerUserAdded(User user) {
        for (UserEventListener listener : eventListeners) {
            try {
                listener.onUserAdded(user);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying event listener", e);
            }
        }
    }
    
    /**
     * Internal: notify event listener of user updated
     */
    private void notifyEventListenerUserUpdated(User user) {
        for (UserEventListener listener : eventListeners) {
            try {
                listener.onUserUpdated(user);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying event listener", e);
            }
        }
    }
    
    /**
     * Internal: notify event listener of user deleted
     */
    private void notifyEventListenerUserDeleted(String userId) {
        for (UserEventListener listener : eventListeners) {
            try {
                listener.onUserDeleted(userId);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying event listener", e);
            }
        }
    }
    
    /**
     * Stop realtime synchronization and cleanup
     */
    public void stop() {
        isConnected.set(false);
        if (realtimeClient != null) {
            realtimeClient.disconnect();
        }
        listListeners.clear();
        eventListeners.clear();
    }
}
