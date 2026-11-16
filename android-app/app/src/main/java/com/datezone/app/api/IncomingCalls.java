package com.datezone.app.api;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.datezone.app.ActiveCallManager;
import com.datezone.app.ChatCallActivity;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cuộc gọi đến: hỏi Nghe / Từ chối ở bất kỳ màn hình nào đang mở.
 * Nguồn: sự kiện call.incoming qua WebSocket (DateZoneApp), hoặc check() hỏi GET /calls/active khi vừa nối lại.
 */
public final class IncomingCalls {
    private static long promptedCallId;
    private static AlertDialog dialog;

    private IncomingCalls() { }

    /** Hỏi máy chủ xem có ai đang gọi mình không (dùng khi vừa mở app / vừa nối lại WebSocket). */
    public static void check(Activity owner) {
        if (owner.isFinishing() || owner.isDestroyed() || ActiveCallManager.getInstance().isCallActive()) return;
        if (new SessionStore(owner).accessToken().isEmpty()) return;
        new ApiClient(owner).request(owner, "GET", "calls/active", null, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                JSONObject data = response.optJSONObject("data");
                if (data == null) return;
                onIncoming(owner, data.optJSONObject("call"), data.optJSONObject("partner"), data.optJSONArray("iceServers"));
            }
            @Override public void onError(String message) { }
        });
    }

    /** Có cuộc gọi đang đổ chuông tới mình -> hiện hộp thoại (mỗi cuộc gọi chỉ hỏi 1 lần). */
    public static void onIncoming(Activity owner, JSONObject call, JSONObject partner, JSONArray iceServers) {
        if (call == null || owner.isFinishing() || owner.isDestroyed()) return;
        if (!"ringing".equals(call.optString("status")) || call.optBoolean("isCaller")) return;
        long callId = call.optLong("id");
        if (callId <= 0 || callId == promptedCallId) return;
        promptedCallId = callId;
        ApiClient api = new ApiClient(owner);
        if (ActiveCallManager.getInstance().isCallActive()) { // đang trong cuộc gọi khác -> báo bận
            api.request(owner, "POST", "calls/" + callId + "/reject", null, silent());
            return;
        }
        boolean video = "video".equals(call.optString("type"));
        String name = ChatUi.text(partner, "displayName");
        String caller = name.isEmpty() ? "Người tương hợp" : name;
        dismiss();
        dialog = new AlertDialog.Builder(owner)
                .setTitle(video ? "Cuộc gọi video đến" : "Cuộc gọi thoại đến")
                .setMessage(caller + " đang gọi cho bạn")
                .setCancelable(false)
                .setPositiveButton("Nghe", (d, which) ->
                        api.request(owner, "POST", "calls/" + callId + "/accept", null, new ApiClient.Callback() {
                            @Override public void onSuccess(JSONObject response) {
                                JSONObject data = response.optJSONObject("data");
                                JSONArray servers = data == null ? null : data.optJSONArray("iceServers");
                                if (servers == null) servers = iceServers;
                                Intent intent = new Intent(owner, ChatCallActivity.class);
                                intent.putExtra(ChatCallActivity.EXTRA_NAME, caller);
                                intent.putExtra(ChatCallActivity.EXTRA_IS_VIDEO_CALL, video);
                                intent.putExtra(ChatCallActivity.EXTRA_MATCH_ID, call.optLong("matchId"));
                                intent.putExtra(ChatCallActivity.EXTRA_CALL_ID, callId);
                                intent.putExtra(ChatCallActivity.EXTRA_INCOMING, true);
                                intent.putExtra(ChatCallActivity.EXTRA_ICE_SERVERS, servers == null ? "[]" : servers.toString());
                                intent.putExtra(ChatCallActivity.EXTRA_PHOTO_URL, ChatUi.text(partner, "avatarUrl"));
                                owner.startActivity(intent);
                            }
                            @Override public void onError(String message) {
                                Toast.makeText(owner, message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Từ chối", (d, which) ->
                        api.request(owner, "POST", "calls/" + callId + "/reject", null, silent()))
                .show();
    }

    /** Người gọi đã cúp / hết chuông trước khi mình trả lời -> đóng hộp thoại. */
    public static void onCallOver(long callId) {
        if (callId == promptedCallId) dismiss();
    }

    private static void dismiss() {
        AlertDialog current = dialog;
        dialog = null;
        try { if (current != null && current.isShowing()) current.dismiss(); }
        catch (RuntimeException ignored) { /* màn hình chứa hộp thoại đã đóng */ }
    }

    private static ApiClient.Callback silent() {
        return new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) { }
            @Override public void onError(String message) { }
        };
    }
}
