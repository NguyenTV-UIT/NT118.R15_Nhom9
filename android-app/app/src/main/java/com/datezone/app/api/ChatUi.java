package com.datezone.app.api;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.datezone.app.ChatDetailActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Tiện ích dùng chung cho các màn chat lấy dữ liệu từ máy chủ. */
public final class ChatUi {
    private static final int[] AVATAR_COLORS = {0xFFD97256, 0xFFBB4A4A, 0xFF8B684E, 0xFF596F82, 0xFF9B7853, 0xFF8EABB9};

    private ChatUi() { }

    public static String text(JSONObject data, String key) {
        return data == null || data.isNull(key) ? "" : data.optString(key, "");
    }

    /** Ảnh đại diện của 1 hồ sơ công khai (avatarUrl, hoặc ảnh đầu tiên). */
    public static String avatar(JSONObject user) {
        String url = text(user, "avatarUrl");
        if (!url.isEmpty() || user == null) return url;
        JSONArray photos = user.optJSONArray("photos");
        JSONObject first = photos == null ? null : photos.optJSONObject(0);
        return text(first, "url");
    }

    public static String initial(String name) {
        return name == null || name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    public static int colorFor(String name) {
        return AVATAR_COLORS[Math.abs((name == null ? "" : name).hashCode() % AVATAR_COLORS.length)];
    }

    /** Có ảnh thì hiện ảnh, không thì hiện chữ cái đầu trên nền màu. */
    public static void bindAvatar(ImageView image, TextView letter, String url, String name) {
        boolean hasPhoto = url != null && !url.isEmpty();
        if (image != null) {
            image.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
            if (hasPhoto) RemoteImages.load(image, url);
        }
        if (letter != null) {
            letter.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
            letter.setText(initial(name));
            if (letter.getBackground() != null) letter.getBackground().mutate().setTint(colorFor(name));
        }
    }

    public static Date parseTime(String iso) {
        if (iso == null || iso.length() < 19) return null;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.parse(iso.substring(0, 19));
        } catch (Exception e) { return null; }
    }

    /** "10:42" nếu trong hôm nay, "Hôm qua", còn lại "dd/MM". */
    public static String timeLabel(String iso) {
        Date date = parseTime(iso);
        if (date == null) return "";
        Calendar then = Calendar.getInstance();
        then.setTime(date);
        Calendar now = Calendar.getInstance();
        if (sameDay(then, now)) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(then, now)) return "Hôm qua";
        return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(date);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Mở màn chat thật của 1 tương hợp. user = hồ sơ công khai của người kia (có thể null). */
    public static Intent chatIntent(Context context, long matchId, JSONObject user) {
        Intent intent = new Intent(context, ChatDetailActivity.class);
        intent.putExtra(ChatDetailActivity.EXTRA_MATCH_ID, matchId);
        if (user != null) {
            intent.putExtra(ChatDetailActivity.EXTRA_NAME, text(user, "displayName"));
            intent.putExtra(ChatDetailActivity.EXTRA_AGE, user.isNull("age") ? "" : String.valueOf(user.optInt("age")));
            intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_URL, avatar(user));
            intent.putExtra(ChatDetailActivity.EXTRA_PARTNER_ID, user.optLong("id"));
        }
        return intent;
    }
}
