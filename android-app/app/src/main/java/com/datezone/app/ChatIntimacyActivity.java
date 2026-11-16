package com.datezone.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ChatIntimacyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.chat_activity_intimacy);

        View root = findViewById(R.id.chatIntimacyRoot);
        if (root != null) {
            int left = root.getPaddingLeft();
            int top = root.getPaddingTop();
            int right = root.getPaddingRight();
            int bottom = root.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.ime()
                );

                view.setPadding(
                        left + bars.left,
                        top + bars.top,
                        right + bars.right,
                        bottom + bars.bottom
                );

                return insets;
            });
            ViewCompat.requestApplyInsets(root);
        }

        // Nút Back
        View btnBack = findViewById(R.id.btnBackIntimacy);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        loadIntimacy();
    }

    /** GET /chats/intimacy - thay các thẻ mẫu bằng dữ liệu thật. */
    private void loadIntimacy() {
        android.widget.LinearLayout content = findViewById(R.id.layoutIntimacyContent);
        android.widget.TextView tvCount = findViewById(R.id.tvIntimacyCloseCount);
        if (content == null) return;
        while (content.getChildCount() > 1) content.removeViewAt(1); // giữ lại thẻ banner đầu trang
        if (tvCount != null) tvCount.setText("Đang tải…");
        new com.datezone.app.api.ApiClient(this).request(this, "GET", "chats/intimacy", null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                org.json.JSONObject data = response.getJSONObject("data");
                org.json.JSONArray close = data.getJSONArray("closeFriends");
                org.json.JSONArray progress = data.getJSONArray("inProgress");
                if (tvCount != null) tvCount.setText(close.length() + " người thân thiết");
                while (content.getChildCount() > 1) content.removeViewAt(1);
                if (close.length() > 0) addSectionTitle(content, "ĐÃ ĐỦ ĐỘ THÂN THIẾT");
                for (int i = 0; i < close.length(); i++) addCard(content, close.getJSONObject(i), true);
                if (progress.length() > 0) addSectionTitle(content, "ĐANG TIẾN TỚI");
                for (int i = 0; i < progress.length(); i++) addCard(content, progress.getJSONObject(i), false);
                if (close.length() + progress.length() == 0)
                    addSectionTitle(content, "Chưa có cuộc trò chuyện nào. Hãy nhắn tin với người bạn đã tương hợp!");
            }
            @Override public void onError(String message) {
                if (tvCount != null) tvCount.setText("Không tải được dữ liệu");
                android.widget.Toast.makeText(ChatIntimacyActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addSectionTitle(android.widget.LinearLayout content, String title) {
        android.widget.TextView view = new android.widget.TextView(this);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (24 * getResources().getDisplayMetrics().density);
        view.setLayoutParams(params);
        view.setText(title);
        view.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary));
        view.setTextSize(12f);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(view);
    }

    private void addCard(android.widget.LinearLayout content, org.json.JSONObject item, boolean closeFriend) throws org.json.JSONException {
        org.json.JSONObject user = item.getJSONObject("user");
        org.json.JSONObject intimacy = item.getJSONObject("intimacy");
        long matchId = item.getLong("matchId");
        String name = com.datezone.app.api.ChatUi.text(user, "displayName");
        String photoUrl = com.datezone.app.api.ChatUi.avatar(user);
        int max = intimacy.optInt("max", 20);

        View card = getLayoutInflater().inflate(R.layout.item_intimacy_card, content, false);
        com.datezone.app.api.ChatUi.bindAvatar(card.findViewById(R.id.ivIntimacyAvatar), card.findViewById(R.id.tvIntimacyLetter), photoUrl, name);
        card.findViewById(R.id.ivIntimacyHeart).setVisibility(closeFriend ? View.VISIBLE : View.GONE);
        ((android.widget.TextView) card.findViewById(R.id.tvIntimacyName)).setText(name);
        ((android.widget.TextView) card.findViewById(R.id.tvIntimacyScore)).setText(closeFriend ? "" : intimacy.optInt("score") + " / " + max + " tin");
        ((android.widget.TextView) card.findViewById(R.id.tvIntimacyCounts)).setText(
                "Bạn " + intimacy.optInt("mine") + "/" + max + " · " + name + " " + intimacy.optInt("partner") + "/" + max);
        ((android.widget.TextView) card.findViewById(R.id.tvIntimacyHint)).setText(com.datezone.app.api.ChatUi.text(item, "hint"));
        android.widget.ProgressBar bar = card.findViewById(R.id.progressIntimacyItem);
        bar.setMax(max);
        bar.setProgress(intimacy.optInt("score"));

        View.OnClickListener openChat = v -> startActivity(com.datezone.app.api.ChatUi.chatIntent(ChatIntimacyActivity.this, matchId, user));
        card.setOnClickListener(openChat);
        if (closeFriend) {
            card.findViewById(R.id.layoutIntimacyActions).setVisibility(View.VISIBLE);
            card.findViewById(R.id.btnIntimacyChat).setOnClickListener(openChat);
            card.findViewById(R.id.btnIntimacyCall).setOnClickListener(v -> openRemoteCall(matchId, name, photoUrl, false));
            card.findViewById(R.id.btnIntimacyVideo).setOnClickListener(v -> openRemoteCall(matchId, name, photoUrl, true));
        }
        content.addView(card);
    }

    private void openRemoteCall(long matchId, String name, String photoUrl, boolean isVideoCall) {
        Intent intent = new Intent(ChatIntimacyActivity.this, ChatCallActivity.class);
        intent.putExtra(ChatCallActivity.EXTRA_NAME, name);
        intent.putExtra(ChatCallActivity.EXTRA_IS_VIDEO_CALL, isVideoCall);
        intent.putExtra(ChatCallActivity.EXTRA_MATCH_ID, matchId);
        intent.putExtra(ChatCallActivity.EXTRA_PHOTO_URL, photoUrl);
        startActivity(intent);
    }

    private void setupActionButtons() {
        // --- NGUYÊN ---
        View btnChatNguyen = findViewById(R.id.btnIntimacyChatNguyen);
        if (btnChatNguyen != null) {
            btnChatNguyen.setOnClickListener(v -> openChatDetail("Nguyên", R.drawable.discovery_photo_1, 20));
        }

        View btnCallNguyen = findViewById(R.id.btnIntimacyCallNguyen);
        if (btnCallNguyen != null) {
            btnCallNguyen.setOnClickListener(v -> openCall("Nguyên", R.drawable.discovery_photo_1, false));
        }

        View btnVideoNguyen = findViewById(R.id.btnIntimacyVideoNguyen);
        if (btnVideoNguyen != null) {
            btnVideoNguyen.setOnClickListener(v -> openCall("Nguyên", R.drawable.discovery_photo_1, true));
        }

        // --- PENELOPE ---
        View btnChatPenelope = findViewById(R.id.btnIntimacyChatPenelope);
        if (btnChatPenelope != null) {
            btnChatPenelope.setOnClickListener(v -> openChatDetail("Penelope", R.drawable.discovery_photo_4, 20));
        }

        View btnCallPenelope = findViewById(R.id.btnIntimacyCallPenelope);
        if (btnCallPenelope != null) {
            btnCallPenelope.setOnClickListener(v -> openCall("Penelope", R.drawable.discovery_photo_4, false));
        }

        View btnVideoPenelope = findViewById(R.id.btnIntimacyVideoPenelope);
        if (btnVideoPenelope != null) {
            btnVideoPenelope.setOnClickListener(v -> openCall("Penelope", R.drawable.discovery_photo_4, true));
        }

        // --- CÁC THẺ ĐANG TIẾN TỚI ---
        View cardGrace = findViewById(R.id.cardIntimacyGrace);
        if (cardGrace != null) {
            cardGrace.setOnClickListener(v -> openChatDetail("Grace", R.drawable.discovery_photo_1, 12));
        }

        View cardAbigail = findViewById(R.id.cardIntimacyAbigail);
        if (cardAbigail != null) {
            cardAbigail.setOnClickListener(v -> openChatDetail("Abigail", R.drawable.discovery_photo_2, 7));
        }

        View cardElizabeth = findViewById(R.id.cardIntimacyElizabeth);
        if (cardElizabeth != null) {
            cardElizabeth.setOnClickListener(v -> openChatDetail("Elizabeth", R.drawable.discovery_photo_3, 5));
        }
    }

    private void openChatDetail(String name, int photoResId, int initialMsgCount) {
        Intent intent = new Intent(ChatIntimacyActivity.this, ChatDetailActivity.class);
        intent.putExtra(ChatDetailActivity.EXTRA_NAME, name);
        intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_RES_ID, photoResId);
        intent.putExtra(ChatDetailActivity.EXTRA_INITIAL_MSG_COUNT, initialMsgCount);
        startActivity(intent);
    }

    private void openCall(String name, int photoResId, boolean isVideoCall) {
        Intent intent = new Intent(ChatIntimacyActivity.this, ChatCallActivity.class);
        intent.putExtra(ChatCallActivity.EXTRA_NAME, name);
        intent.putExtra(ChatCallActivity.EXTRA_PHOTO_RES_ID, photoResId);
        intent.putExtra(ChatCallActivity.EXTRA_IS_VIDEO_CALL, isVideoCall);
        startActivity(intent);
    }
}
