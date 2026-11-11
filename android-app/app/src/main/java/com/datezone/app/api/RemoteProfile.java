package com.datezone.app.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

public final class RemoteProfile {
    public final long id;
    public final String name, age, details, distance, bio;
    public final String[] photos;
    public final ArrayList<String> interests = new ArrayList<>();
    public final JSONObject json;

    public RemoteProfile(JSONObject data) throws JSONException {
        json = data;
        id = data.getLong("id");
        name = string(data, "displayName");
        age = data.isNull("age") ? "" : data.getInt("age") + " tuổi";
        String gender = string(data, "gender");
        String genderLabel = "male".equals(gender) ? "Nam" : "female".equals(gender) ? "Nữ"
                : "other".equals(gender) ? "Phi nhị nguyên" : "";
        String height = data.isNull("heightCm") ? "" : "Cao " + data.getInt("heightCm") + " cm";
        details = height + (!height.isEmpty() && !genderLabel.isEmpty() ? " • " : "") + genderLabel;
        String city = string(data, "city");
        distance = city + (data.isNull("distanceKm") ? "" : (city.isEmpty() ? "" : " • ")
                + "Cách bạn " + data.getInt("distanceKm") + " km");
        bio = string(data, "bio");
        JSONArray list = data.optJSONArray("photos");
        ArrayList<String> urls = new ArrayList<>();
        if (list != null) for (int i = 0; i < list.length(); i++) {
            String url = string(list.getJSONObject(i), "url");
            if (!url.isEmpty()) urls.add(url);
        }
        if (urls.isEmpty() && !string(data, "avatarUrl").isEmpty()) urls.add(data.getString("avatarUrl"));
        photos = urls.toArray(new String[0]);
        JSONArray tags = data.optJSONArray("interests");
        if (tags != null) for (int i = 0; i < tags.length(); i++)
            interests.add(tags.getJSONObject(i).getString("name"));
    }

    private static String string(JSONObject data, String key) {
        return data.isNull(key) ? "" : data.optString(key, "");
    }
}
