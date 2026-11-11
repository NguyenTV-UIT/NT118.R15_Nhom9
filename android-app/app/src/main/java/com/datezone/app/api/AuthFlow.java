package com.datezone.app.api;

import android.app.Activity;
import android.content.Intent;
import com.datezone.app.*;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AuthFlow {
    public static Class<?> destination(JSONObject user) {
        JSONObject onboarding = user.optJSONObject("onboarding");
        if (onboarding != null && onboarding.optBoolean("completed")) return HomeActivity.class;
        JSONArray missing = onboarding == null ? null : onboarding.optJSONArray("missing");
        if (missing == null) return EnterNameActivity.class;
        String[] fields = {"displayName", "photos", "birthday", "gender", "interests"};
        Class<?>[] pages = {EnterNameActivity.class, AddPhotosActivity.class, BirthdayActivity.class,
                GenderActivity.class, InterestsActivity.class};
        for (int i = 0; i < fields.length; i++) for (int j = 0; j < missing.length(); j++)
            if (fields[i].equals(missing.optString(j))) return pages[i];
        return ProfileReadyActivity.class;
    }

    public static void signedIn(Activity owner, JSONObject response) throws Exception {
        JSONObject data = response.getJSONObject("data");
        JSONObject user = data.getJSONObject("user");
        SessionStore session = new SessionStore(owner);
        session.clear();
        session.saveTokens(data.getJSONObject("tokens"));
        session.saveUser(user);
        Intent next = new Intent(owner, destination(user));
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        owner.startActivity(next);
        owner.finish();
    }
}
