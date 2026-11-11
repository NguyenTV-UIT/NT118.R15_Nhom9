package com.datezone.app.api;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.datezone.app.ActiveCallManager;
import com.datezone.app.ChatCallActivity;
import org.json.JSONObject;

/**
 * Kiểm tra cuộc gọi đến bằng GET /calls/active (app chưa nối WebSocket nên các màn chat gọi check() định kỳ).
 * Có người đang gọi -> hỏi Nghe / Từ chối -> báo máy chủ rồi mở màn cuộc gọi.
 */
public final class IncomingCalls {
    private static long promptedCallId;

    private IncomingCalls() { }

    public static void check(Activity owner) {
        if (owner.isFinishing() || owner.isDestroyed() || ActiveCallManager.getInstance().isCallActive()) return;
        if (new SessionStore(owner).accessToken().isEmpty()) return;
        ApiClient api = new ApiClient(owner);
        api.request(owner, "GET", "calls/active", null, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                JSONObject data = response.getJSONObject("data");
                JSONObject call = data.optJSONObject("call");
                if (call == null || !"ringing".equals(call.optString("status")) || call.optBoolean("isCaller")) return;
                long callId = call.getLong("id");
                if (callId == promptedCallId) return;
                promptedCallId = callId;
                prompt(owner, api, call, data.optJSONObject("partner"));
            }
            @Override public void onError(String message) { }
        });
    }

    private static void prompt(Activity owner, ApiClient api, JSONObject call, JSONObject partner) {
        long callId = call.optLong("id");
        boolean video = "video".equals(call.optString("type"));
        String name = ChatUi.text(partner, "displayName");
        if (name.isEmpty()) name = "Người tương hợp";
        String caller = name;
        new AlertDialog.Builder(owner)
                .setTitle(video ? "Cuộc gọi video đến" : "Cuộc gọi thoại đến")
                .setMessage(caller + " đang gọi cho bạn")
                .setCancelable(false)
                .setPositiveButton("Nghe", (dialog, which) ->
                        api.request(owner, "POST", "calls/" + callId + "/accept", null, new ApiClient.Callback() {
                            @Override public void onSuccess(JSONObject response) {
                                Intent intent = new Intent(owner, ChatCallActivity.class);
                                intent.putExtra(ChatCallActivity.EXTRA_NAME, caller);
                                intent.putExtra(ChatCallActivity.EXTRA_IS_VIDEO_CALL, video);
                                intent.putExtra(ChatCallActivity.EXTRA_MATCH_ID, call.optLong("matchId"));
                                intent.putExtra(ChatCallActivity.EXTRA_CALL_ID, callId);
                                intent.putExtra(ChatCallActivity.EXTRA_PHOTO_URL, ChatUi.text(partner, "avatarUrl"));
                                owner.startActivity(intent);
                            }
                            @Override public void onError(String message) {
                                Toast.makeText(owner, message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Từ chối", (dialog, which) ->
                        api.request(owner, "POST", "calls/" + callId + "/reject", null, new ApiClient.Callback() {
                            @Override public void onSuccess(JSONObject response) { }
                            @Override public void onError(String message) { }
                        }))
                .show();
    }
}
