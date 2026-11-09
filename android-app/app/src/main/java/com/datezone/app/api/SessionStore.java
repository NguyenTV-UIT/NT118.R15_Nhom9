package com.datezone.app.api;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

/** Shared contract with the login flow. Pass response.data.tokens to saveTokens(). */
public final class SessionStore {
    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("datezone_session", Context.MODE_PRIVATE);
    }

    public String accessToken() { return prefs.getString("accessToken", ""); }
    public String refreshToken() { return prefs.getString("refreshToken", ""); }
    public JSONObject user() {
        try { return new JSONObject(prefs.getString("user", "{}")); }
        catch (JSONException e) { return new JSONObject(); }
    }
    public void saveUser(JSONObject user) { prefs.edit().putString("user", user.toString()).apply(); }

    public void saveTokens(JSONObject tokens) throws JSONException {
        String access = tokens.getString("accessToken");
        String refresh = tokens.getString("refreshToken");
        if (access.isEmpty() || refresh.isEmpty()) throw new JSONException("Thiếu token đăng nhập");
        synchronized (SessionStore.class) {
            prefs.edit().putString("accessToken", access).putString("refreshToken", refresh).apply();
        }
    }

    String[] snapshot() {
        synchronized (SessionStore.class) { return new String[]{accessToken(), generation()}; }
    }
    String generation() { return prefs.getString("generation", ""); }
    boolean saveRefreshedTokens(String generation, JSONObject tokens) throws JSONException {
        synchronized (SessionStore.class) {
            if (!generation.equals(generation())) return false;
            saveTokens(tokens);
            return true;
        }
    }
    void clearIfCurrent(String generation) {
        synchronized (SessionStore.class) { if (generation.equals(generation())) clear(); }
    }
    public void clear() {
        synchronized (SessionStore.class) {
            prefs.edit().clear().putString("generation", java.util.UUID.randomUUID().toString()).apply();
        }
    }
}
