package com.datezone.app.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Map;

/** Converts editor fields to a PATCH-style profile body, preserving fields absent from the form. */
public final class ProfileForm {
    public static final String[] OPTION_KEYS = {"relationshipGoal", "zodiac", "smoking", "drinking", "exercise", "education"};

    public static String text(JSONObject data, String key) {
        return data.isNull(key) ? "" : data.optString(key, "");
    }

    public static String label(JSONObject options, String key, String code) {
        JSONArray choices = options.optJSONArray(key);
        if (choices != null) for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            if (choice != null && code.equals(choice.optString("code")))
                return choice.optString("shortLabel", choice.optString("label"));
        }
        return "";
    }

    public static JSONObject patch(JSONObject original, JSONObject options, Map<String, String> fields,
                                    JSONObject optionCodes) throws JSONException {
        JSONObject result = new JSONObject();
        String name = fields.get("displayName").trim();
        if (name.length() < 2 || name.length() > 100) throw new IllegalArgumentException("Tên cần từ 2 đến 100 ký tự.");
        changed(result, original, "displayName", name);
        String bio = fields.get("bio").trim();
        if (bio.length() > 300) throw new IllegalArgumentException("Giới thiệu tối đa 300 ký tự.");
        changed(result, original, "bio", bio.isEmpty() ? JSONObject.NULL : bio);
        String city = fields.get("city").trim();
        if (city.length() > 100) throw new IllegalArgumentException("Khu vực tối đa 100 ký tự.");
        changed(result, original, "city", city.isEmpty() ? JSONObject.NULL : city);
        if (result.has("city")) result.put("latitude", JSONObject.NULL).put("longitude", JSONObject.NULL);
        changed(result, original, "heightCm", measurement(fields.get("heightCm"), 140, 200, "Chiều cao"));
        changed(result, original, "weightKg", measurement(fields.get("weightKg"), 35, 120, "Cân nặng"));
        String gender = fields.get("gender").trim();
        if (!gender.isEmpty()) {
            String code = codeOf(options, "gender", gender);
            if (code.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn giới tính trong danh sách.");
            changed(result, original, "gender", code);
        } else if (!original.isNull("gender")) throw new IllegalArgumentException("Vui lòng chọn giới tính.");
        for (String key : OPTION_KEYS) {
            Object value = optionCodes.opt(key);
            if (value == null) value = original.opt(key);
            if (value == null) value = JSONObject.NULL;
            if (!JSONObject.NULL.equals(value) && codeOf(options, key, value.toString()).isEmpty()) {
                // Preserve a previously saved option unknown to an older catalogue.
                if (value.equals(original.opt(key))) continue;
                throw new IllegalArgumentException("Lựa chọn hồ sơ không hợp lệ.");
            }
            changed(result, original, key, value);
        }
        return result;
    }

    private static Object measurement(String input, int min, int max, String label) {
        String value = input.trim().replaceFirst("(?i)\\s*(cm|kg)$", "").trim();
        if (value.isEmpty()) return JSONObject.NULL;
        try {
            if (!value.matches("\\d+")) throw new NumberFormatException();
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " cần là số từ " + min + " đến " + max + ".");
        }
    }

    private static String codeOf(JSONObject options, String key, String input) {
        JSONArray choices = options.optJSONArray(key);
        if (choices != null) for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            if (choice != null && (input.equalsIgnoreCase(choice.optString("code"))
                    || input.equalsIgnoreCase(choice.optString("label"))
                    || input.equalsIgnoreCase(choice.optString("shortLabel")))) return choice.optString("code");
        }
        return "";
    }

    private static void changed(JSONObject result, JSONObject original, String key, Object value) throws JSONException {
        Object previous = original.opt(key);
        if (previous == null) previous = JSONObject.NULL;
        if (!previous.toString().equals(value.toString())) result.put(key, value);
    }
}
