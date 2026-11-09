package com.datezone.app.api;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.datezone.app.R;
import org.json.JSONObject;

/** Keeps a form on screen until the server accepts it and prevents double submissions. */
public final class ApiUi {
    public interface Success { void accept(JSONObject response) throws Exception; }

    public static JSONObject data(Object... entries) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < entries.length; i += 2)
                result.put((String) entries[i], entries[i + 1] == null ? JSONObject.NULL : entries[i + 1]);
        } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); }
        return result;
    }

    public static void send(Activity owner, int buttonId, boolean authenticated, String method,
                            String path, JSONObject body, Success success) {
        View root = owner.findViewById(android.R.id.content);
        if (Boolean.TRUE.equals(root.getTag(R.id.api_request_pending))) return;
        root.setTag(R.id.api_request_pending, true);
        View button = owner.findViewById(buttonId);
        CharSequence original = button instanceof TextView ? ((TextView) button).getText() : null;
        if (button != null) button.setEnabled(false);
        if (button instanceof TextView) ((TextView) button).setText("Đang xử lý…");
        ApiClient.Callback callback = new ApiClient.Callback() {
            private void done() {
                root.setTag(R.id.api_request_pending, false);
                if (button != null) button.setEnabled(true);
                if (button instanceof TextView) ((TextView) button).setText(original);
            }
            @Override public void onSuccess(JSONObject response) throws Exception {
                done();
                success.accept(response);
            }
            @Override public void onError(String message) {
                done();
                Toast.makeText(owner, message, Toast.LENGTH_LONG).show();
            }
        };
        ApiClient api = new ApiClient(owner);
        if (authenticated) api.request(owner, method, path, body, callback);
        else api.publicRequest(owner, method, path, body, callback);
    }

    public static void saveProfile(Activity owner, JSONObject body, Intent next) {
        save(owner, "users/me/profile", body, next);
    }

    public static void save(Activity owner, String path, JSONObject body, Intent next) {
        int buttonId = owner.findViewById(R.id.btnFinish) != null ? R.id.btnFinish : R.id.btnContinue;
        send(owner, buttonId, true, "PUT", path, body, response -> {
            new SessionStore(owner).saveUser(response.getJSONObject("data"));
            owner.startActivity(next);
        });
    }
}
