package com.datezone.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.card.MaterialCardView;

public class ChatDetailActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "EXTRA_NAME";
    public static final String EXTRA_AGE = "EXTRA_AGE";
    public static final String EXTRA_PHOTO_RES_ID = "EXTRA_PHOTO_RES_ID";
    public static final String EXTRA_INITIAL = "EXTRA_INITIAL";
    public static final String EXTRA_BG_COLOR = "EXTRA_BG_COLOR";
    public static final String EXTRA_MY_SENT_COUNT = "EXTRA_MY_SENT_COUNT";
    public static final String EXTRA_PARTNER_SENT_COUNT = "EXTRA_PARTNER_SENT_COUNT";
    public static final String EXTRA_INITIAL_MSG_COUNT = "EXTRA_INITIAL_MSG_COUNT";
    /** Có EXTRA_MATCH_ID (> 0) thì màn này dùng dữ liệu thật từ máy chủ, không thì chạy dữ liệu mẫu như cũ. */
    public static final String EXTRA_MATCH_ID = "EXTRA_MATCH_ID";
    public static final String EXTRA_PHOTO_URL = "EXTRA_PHOTO_URL";
    public static final String EXTRA_PARTNER_ID = "EXTRA_PARTNER_ID";
    private static final long POLL_INTERVAL_MS = 3000;

    private long matchId;
    private boolean remote;
    private String targetPhotoUrl = "";
    private org.json.JSONObject partnerJson;
    private com.datezone.app.api.ApiClient api;
    private long lastMessageId;
    private boolean intimacyLoaded;
    private boolean sending;
    private boolean polling;
    private final java.util.HashSet<Long> shownMessageIds = new java.util.HashSet<>();
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            pollNewMessages();
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private String targetName = "Grace";
    private String targetAge = "26";
    private int targetPhotoResId = R.drawable.discovery_photo_1;
    private String targetInitial = null;
    private int targetBgColor = 0;

    // Tiến độ được tính theo bên nhắn ít hơn: Math.min(mySentCount, partnerSentCount)
    private int mySentCount = 14;
    private int partnerSentCount = 12;

    private ImageView ivChatAvatar;
    private TextView tvChatAvatarLetter;
    private ImageView ivHeaderHeartBadge;
    private TextView tvChatPartnerName;

    private MaterialCardView cardIntimacyProgress;
    private TextView tvIntimacyCountBadge;
    private ImageButton btnToggleIntimacyCard;
    private LinearLayout layoutMilestoneDetails;
    private ProgressBar progressIntimacy;

    private ImageView ivCheckpoint10;
    private TextView tvMilestone10Status;
    private ImageView ivCheckpoint20;
    private TextView tvMilestone20Status;

    private View btnVoiceCallContainer;
    private View btnVideoCallContainer;
    private View btnAttachPhotoContainer;

    private ImageButton btnVoiceCall;
    private ImageButton btnVideoCall;
    private ImageButton btnAttachPhoto;

    private ImageView ivLockVoice;
    private ImageView ivLockVideo;
    private ImageView ivLockAttachPhoto;

    private EditText edtChatMessage;
    private LinearLayout layoutMessagesList;
    private NestedScrollView scrollMessages;

    private boolean isPhotoUnlockedDialogShown = false;
    private boolean isIntimacyCompletedDialogShown = false;
    private ActivityResultLauncher<String> photoPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.chat_activity_detail);

        View root = findViewById(R.id.chatDetailRoot);
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

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_NAME)) {
                String name = intent.getStringExtra(EXTRA_NAME);
                if (name != null && !name.isEmpty()) targetName = name;
            }
            if (intent.hasExtra(EXTRA_AGE)) {
                String age = intent.getStringExtra(EXTRA_AGE);
                if (age != null && !age.isEmpty()) targetAge = age;
            }
            if (intent.hasExtra(EXTRA_PHOTO_RES_ID)) {
                targetPhotoResId = intent.getIntExtra(EXTRA_PHOTO_RES_ID, 0);
            }
            if (intent.hasExtra(EXTRA_INITIAL)) {
                targetInitial = intent.getStringExtra(EXTRA_INITIAL);
            }
            if (intent.hasExtra(EXTRA_BG_COLOR)) {
                targetBgColor = intent.getIntExtra(EXTRA_BG_COLOR, 0);
            }
            if (intent.hasExtra(EXTRA_MY_SENT_COUNT)) {
                mySentCount = intent.getIntExtra(EXTRA_MY_SENT_COUNT, 14);
            }
            if (intent.hasExtra(EXTRA_PARTNER_SENT_COUNT)) {
                partnerSentCount = intent.getIntExtra(EXTRA_PARTNER_SENT_COUNT, 12);
            }
            if (intent.hasExtra(EXTRA_INITIAL_MSG_COUNT)) {
                int initialCount = intent.getIntExtra(EXTRA_INITIAL_MSG_COUNT, 0);
                mySentCount = initialCount;
                partnerSentCount = initialCount;
            }
        }

        matchId = intent != null ? intent.getLongExtra(EXTRA_MATCH_ID, 0) : 0;
        remote = matchId > 0;
        if (remote) {
            api = new com.datezone.app.api.ApiClient(this);
            String url = intent.getStringExtra(EXTRA_PHOTO_URL);
            targetPhotoUrl = url == null ? "" : url;
            targetPhotoResId = 0;
            if (!intent.hasExtra(EXTRA_NAME)) targetName = "";
            if (!intent.hasExtra(EXTRA_AGE)) targetAge = "";
            // Số tin thật lấy từ máy chủ ở loadChat()
            mySentCount = 0;
            partnerSentCount = 0;
        }

        int intimacyScore = Math.min(mySentCount, partnerSentCount);
        if (intimacyScore >= 10) isPhotoUnlockedDialogShown = true;
        if (intimacyScore >= 20) isIntimacyCompletedDialogShown = true;

        // Bộ chọn Ảnh/Video từ album thiết bị
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        sendPhotoOrVideoMessage(uri);
                    }
                }
        );

        initViews();
        setupPartnerHeader();
        if (remote) {
            if (layoutMessagesList != null) layoutMessagesList.removeAllViews();
            loadChat();
        } else {
            setupSampleMessages();
        }
        updateIntimacyUI();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (remote && polling) {
            pollHandler.removeCallbacks(pollTask);
            pollHandler.postDelayed(pollTask, POLL_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollTask);
    }

    // ═══════════════ DỮ LIỆU THẬT TỪ MÁY CHỦ ═══════════════

    /** GET /chats/:matchId - người kia + độ thân thiết, sau đó tải tin nhắn. */
    private void loadChat() {
        api.request(this, "GET", "chats/" + matchId, null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                org.json.JSONObject data = response.getJSONObject("data");
                partnerJson = data.getJSONObject("user");
                targetName = com.datezone.app.api.ChatUi.text(partnerJson, "displayName");
                targetAge = partnerJson.isNull("age") ? "" : String.valueOf(partnerJson.optInt("age"));
                targetPhotoUrl = com.datezone.app.api.ChatUi.avatar(partnerJson);
                setupPartnerHeader();
                applyIntimacy(data.optJSONObject("intimacy"));
                loadMessages();
            }
            @Override public void onError(String message) {
                Toast.makeText(ChatDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** GET /chats/:matchId/messages - trang tin nhắn mới nhất (cũ -> mới). */
    private void loadMessages() {
        api.request(this, "GET", "chats/" + matchId + "/messages?limit=50", null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                org.json.JSONArray items = response.getJSONArray("data");
                boolean hasIncoming = false;
                for (int i = 0; i < items.length(); i++) hasIncoming |= showMessage(items.getJSONObject(i));
                if (items.length() == 0) addSystemNoticePill("Hai bạn đã tương hợp. Hãy gửi lời chào đầu tiên!");
                if (hasIncoming) markRead();
                polling = true;
                pollHandler.removeCallbacks(pollTask);
                pollHandler.postDelayed(pollTask, POLL_INTERVAL_MS);
            }
            @Override public void onError(String message) {
                Toast.makeText(ChatDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Cứ 3 giây hỏi máy chủ tin mới hơn tin cuối đang hiển thị (GET ...?after=<id>). */
    private void pollNewMessages() {
        if (!remote || isFinishing()) return;
        com.datezone.app.api.IncomingCalls.check(this);
        api.request(this, "GET", "chats/" + matchId + "/messages?limit=50&after=" + lastMessageId, null,
                new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                        org.json.JSONArray items = response.getJSONArray("data");
                        boolean hasIncoming = false;
                        for (int i = 0; i < items.length(); i++) hasIncoming |= showMessage(items.getJSONObject(i));
                        if (hasIncoming) markRead();
                        org.json.JSONObject meta = response.optJSONObject("meta");
                        if (meta != null) applyIntimacy(meta.optJSONObject("intimacy"));
                    }
                    @Override public void onError(String message) {
                        // Mất mạng tạm thời: lần hỏi sau sẽ lấy bù, không làm phiền người dùng.
                    }
                });
    }

    private void markRead() {
        api.request(this, "POST", "chats/" + matchId + "/read", null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) { }
            @Override public void onError(String message) { }
        });
    }

    /** Hiển thị 1 tin nhắn từ máy chủ (bỏ qua nếu đã hiển thị). Trả về true nếu là tin của người kia. */
    private boolean showMessage(org.json.JSONObject message) {
        long id = message.optLong("id");
        if (id <= 0 || !shownMessageIds.add(id)) return false;
        if (id > lastMessageId) lastMessageId = id;
        boolean fromMe = message.optBoolean("fromMe");
        String type = message.optString("type", "text");
        if ("image".equals(type)) {
            addRemotePhoto(com.datezone.app.api.ChatUi.text(message, "imageUrl"), fromMe);
        } else if ("call".equals(type)) {
            org.json.JSONObject call = message.optJSONObject("call");
            int seconds = call == null ? 0 : call.optInt("durationSeconds");
            String label = com.datezone.app.api.ChatUi.text(message, "preview");
            if (seconds > 0) label += String.format(java.util.Locale.US, " · %02d:%02d", seconds / 60, seconds % 60);
            addSystemNoticePill("📞 " + label);
        } else if (fromMe) {
            addSentBubble(com.datezone.app.api.ChatUi.text(message, "content"));
        } else {
            addReceivedBubble(com.datezone.app.api.ChatUi.text(message, "content"));
        }
        return !fromMe;
    }

    /** Cập nhật bộ đếm từ máy chủ và hiện dialog chúc mừng khi vừa chạm mốc 10 / 20. */
    private void applyIntimacy(org.json.JSONObject intimacy) {
        if (intimacy == null) return;
        mySentCount = intimacy.optInt("mine");
        partnerSentCount = intimacy.optInt("partner");
        if (!intimacyLoaded) {
            intimacyLoaded = true;
            int score = Math.min(mySentCount, partnerSentCount);
            isPhotoUnlockedDialogShown = score >= 10;
            isIntimacyCompletedDialogShown = score >= 20;
            updateIntimacyUI();
            return;
        }
        checkAndTriggerDialogs();
    }

    private void addRemotePhoto(String url, boolean fromMe) {
        ImageView photoView = new ImageView(this);
        photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(480, 320);
        params.gravity = fromMe ? Gravity.END : Gravity.START;
        params.setMargins(fromMe ? 100 : 0, 12, fromMe ? 0 : 100, 12);
        photoView.setLayoutParams(params);
        com.datezone.app.api.RemoteImages.load(photoView, url);
        photoView.setOnClickListener(v -> {
            Intent viewer = new Intent(ChatDetailActivity.this, PhotoViewerActivity.class);
            viewer.putExtra(PhotoViewerActivity.EXTRA_PHOTO_URLS, new String[]{url});
            startActivity(viewer);
        });
        layoutMessagesList.addView(photoView);
        scrollToBottom();
    }

    private void sendRemoteText(String text) {
        if (sending) return;
        sending = true;
        edtChatMessage.setText("");
        org.json.JSONObject body = com.datezone.app.api.ApiUi.data("content", text, "clientId", java.util.UUID.randomUUID().toString());
        api.request(this, "POST", "chats/" + matchId + "/messages", body, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                sending = false;
                org.json.JSONObject data = response.getJSONObject("data");
                showMessage(data.getJSONObject("message"));
                applyIntimacy(data.optJSONObject("intimacy"));
            }
            @Override public void onError(String message) {
                sending = false;
                // Gửi lỗi: trả nội dung về ô nhập để người dùng thử lại
                if (edtChatMessage.getText().length() == 0) edtChatMessage.setText(text);
                Toast.makeText(ChatDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendRemotePhoto(Uri uri) {
        Toast.makeText(this, "Đang gửi ảnh…", Toast.LENGTH_SHORT).show();
        api.uploadPhotoTo(this, "chats/" + matchId + "/photos", uri, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                org.json.JSONObject data = response.getJSONObject("data");
                showMessage(data.getJSONObject("message"));
                applyIntimacy(data.optJSONObject("intimacy"));
            }
            @Override public void onError(String message) {
                Toast.makeText(ChatDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startRemoteCall(boolean video) {
        Intent call = new Intent(ChatDetailActivity.this, ChatCallActivity.class);
        call.putExtra(ChatCallActivity.EXTRA_NAME, targetName);
        call.putExtra(ChatCallActivity.EXTRA_IS_VIDEO_CALL, video);
        call.putExtra(ChatCallActivity.EXTRA_MATCH_ID, matchId);
        call.putExtra(ChatCallActivity.EXTRA_PHOTO_URL, targetPhotoUrl);
        startActivity(call);
    }

    private void initViews() {
        ivChatAvatar = findViewById(R.id.ivChatAvatar);
        tvChatAvatarLetter = findViewById(R.id.tvChatAvatarLetter);
        ivHeaderHeartBadge = findViewById(R.id.ivHeaderHeartBadge);
        tvChatPartnerName = findViewById(R.id.tvChatPartnerName);

        cardIntimacyProgress = findViewById(R.id.cardIntimacyProgress);
        tvIntimacyCountBadge = findViewById(R.id.tvIntimacyCountBadge);
        btnToggleIntimacyCard = findViewById(R.id.btnToggleIntimacyCard);
        layoutMilestoneDetails = findViewById(R.id.layoutMilestoneDetails);
        progressIntimacy = findViewById(R.id.progressIntimacy);

        ivCheckpoint10 = findViewById(R.id.ivCheckpoint10);
        tvMilestone10Status = findViewById(R.id.tvMilestone10Status);
        ivCheckpoint20 = findViewById(R.id.ivCheckpoint20);
        tvMilestone20Status = findViewById(R.id.tvMilestone20Status);

        btnVoiceCallContainer = findViewById(R.id.btnVoiceCallContainer);
        btnVideoCallContainer = findViewById(R.id.btnVideoCallContainer);
        btnAttachPhotoContainer = findViewById(R.id.btnAttachPhotoContainer);

        btnVoiceCall = findViewById(R.id.btnVoiceCall);
        btnVideoCall = findViewById(R.id.btnVideoCall);
        btnAttachPhoto = findViewById(R.id.btnAttachPhoto);

        ivLockVoice = findViewById(R.id.ivLockVoice);
        ivLockVideo = findViewById(R.id.ivLockVideo);
        ivLockAttachPhoto = findViewById(R.id.ivLockAttachPhoto);

        edtChatMessage = findViewById(R.id.edtChatMessage);
        layoutMessagesList = findViewById(R.id.layoutMessagesList);
        scrollMessages = findViewById(R.id.scrollMessages);
    }

    private void setupPartnerHeader() {
        if (tvChatPartnerName != null) {
            tvChatPartnerName.setText(targetAge == null || targetAge.isEmpty() ? targetName : targetName + ", " + targetAge);
        }
        if (remote) {
            if (tvChatAvatarLetter != null) tvChatAvatarLetter.setBackgroundResource(R.drawable.bg_circle_avatar_grace);
            com.datezone.app.api.ChatUi.bindAvatar(ivChatAvatar, tvChatAvatarLetter, targetPhotoUrl, targetName);
            return;
        }

        if (targetPhotoResId != 0) {
            if (ivChatAvatar != null) {
                ivChatAvatar.setVisibility(View.VISIBLE);
                ivChatAvatar.setImageResource(targetPhotoResId);
            }
            if (tvChatAvatarLetter != null) {
                tvChatAvatarLetter.setVisibility(View.GONE);
            }
        } else {
            if (ivChatAvatar != null) {
                ivChatAvatar.setVisibility(View.GONE);
            }
            if (tvChatAvatarLetter != null) {
                tvChatAvatarLetter.setVisibility(View.VISIBLE);
                tvChatAvatarLetter.setText(targetInitial != null ? targetInitial : "A");

                if ("A".equals(targetInitial)) {
                    tvChatAvatarLetter.setBackgroundResource(R.drawable.bg_circle_avatar_abigail);
                } else if ("E".equals(targetInitial)) {
                    tvChatAvatarLetter.setBackgroundResource(R.drawable.bg_circle_avatar_elizabeth);
                } else if ("P".equals(targetInitial)) {
                    tvChatAvatarLetter.setBackgroundResource(R.drawable.bg_circle_avatar_penelope);
                } else if ("C".equals(targetInitial)) {
                    tvChatAvatarLetter.setBackgroundResource(R.drawable.bg_circle_avatar_chloe);
                } else if ("G".equals(targetInitial)) {
                    tvChatAvatarLetter.setBackgroundResource(R.drawable.bg_circle_avatar_grace);
                } else {
                    tvChatAvatarLetter.setBackgroundResource(R.drawable.bg_circle_avatar_grace);
                    if (tvChatAvatarLetter.getBackground() != null && targetBgColor != 0) {
                        tvChatAvatarLetter.getBackground().mutate().setTint(targetBgColor);
                    }
                }
            }
        }
    }

    private void updateIntimacyUI() {
        int intimacyScore = Math.min(mySentCount, partnerSentCount);
        boolean isFullIntimacy = intimacyScore >= 20;

        if (isFullIntimacy) {
            // CẤP ĐỘ 3: >= 20 tin (ĐÃ THÂN THIẾT HOÀN TOÀN)
            if (cardIntimacyProgress != null) cardIntimacyProgress.setVisibility(View.GONE);
            if (ivHeaderHeartBadge != null) ivHeaderHeartBadge.setVisibility(View.VISIBLE);

            if (ivLockVoice != null) ivLockVoice.setVisibility(View.GONE);
            if (ivLockVideo != null) ivLockVideo.setVisibility(View.GONE);
            if (ivLockAttachPhoto != null) ivLockAttachPhoto.setVisibility(View.GONE);

            if (btnVoiceCall != null) {
                btnVoiceCall.setBackgroundResource(R.drawable.bg_circle_add);
                btnVoiceCall.setColorFilter(Color.WHITE);
                btnVoiceCall.setAlpha(1.0f);
            }
            if (btnVideoCall != null) {
                btnVideoCall.setBackgroundResource(R.drawable.bg_circle_add);
                btnVideoCall.setColorFilter(Color.WHITE);
                btnVideoCall.setAlpha(1.0f);
            }
            if (btnAttachPhoto != null) {
                btnAttachPhoto.setAlpha(1.0f);
            }
            return;
        }

        // CHƯA ĐỦ ĐỘ THÂN THIẾT (< 20 tin)
        if (cardIntimacyProgress != null) cardIntimacyProgress.setVisibility(View.VISIBLE);
        if (ivHeaderHeartBadge != null) ivHeaderHeartBadge.setVisibility(View.GONE);

        if (btnVoiceCall != null) {
            btnVoiceCall.setBackgroundColor(Color.TRANSPARENT);
            btnVoiceCall.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary));
            btnVoiceCall.setAlpha(0.4f);
        }
        if (btnVideoCall != null) {
            btnVideoCall.setBackgroundColor(Color.TRANSPARENT);
            btnVideoCall.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary));
            btnVideoCall.setAlpha(0.4f);
        }

        if (tvIntimacyCountBadge != null) {
            tvIntimacyCountBadge.setText(intimacyScore + " / 20 tin");
        }

        if (progressIntimacy != null) {
            progressIntimacy.setProgress(Math.min(20, intimacyScore));
        }

        // Cột mốc 10 tin (Gửi ảnh)
        boolean hasUnlockedPhoto = intimacyScore >= 10;
        if (ivCheckpoint10 != null) {
            ivCheckpoint10.setImageResource(hasUnlockedPhoto ? R.drawable.ic_check_circle_small : R.drawable.ic_lock_small);
        }
        if (tvMilestone10Status != null) {
            if (hasUnlockedPhoto) {
                tvMilestone10Status.setText("Đã mở khóa");
                tvMilestone10Status.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            } else {
                int remain10 = 10 - intimacyScore;
                tvMilestone10Status.setText("Còn " + remain10 + " tin nữa");
                tvMilestone10Status.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        }

        if (ivLockAttachPhoto != null && btnAttachPhoto != null) {
            if (hasUnlockedPhoto) {
                ivLockAttachPhoto.setVisibility(View.GONE);
                btnAttachPhoto.setAlpha(1.0f);
            } else {
                ivLockAttachPhoto.setVisibility(View.VISIBLE);
                btnAttachPhoto.setAlpha(0.4f);
            }
        }

        // Cột mốc 20 tin (Gọi & Video)
        if (ivCheckpoint20 != null) {
            ivCheckpoint20.setImageResource(R.drawable.ic_lock_small);
        }
        if (tvMilestone20Status != null) {
            int remain20 = 20 - intimacyScore;
            tvMilestone20Status.setText("Còn " + remain20 + " tin nữa");
            tvMilestone20Status.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }

        if (ivLockVoice != null) ivLockVoice.setVisibility(View.VISIBLE);
        if (ivLockVideo != null) ivLockVideo.setVisibility(View.VISIBLE);
    }

    private void setupListeners() {
        // Nút Back
        findViewById(R.id.btnChatBack).setOnClickListener(v -> finish());

        // Mở trang ProfileObjectActivity khi bấm vào Avatar hoặc Tên
        View.OnClickListener openPartnerProfileListener = v -> openPartnerProfile();

        if (ivChatAvatar != null) ivChatAvatar.setOnClickListener(openPartnerProfileListener);
        if (tvChatAvatarLetter != null) tvChatAvatarLetter.setOnClickListener(openPartnerProfileListener);
        if (tvChatPartnerName != null) tvChatPartnerName.setOnClickListener(openPartnerProfileListener);

        // Nút Thu gọn / Mở rộng Khung Độ Thân Thiết
        View.OnClickListener toggleIntimacyListener = v -> {
            if (layoutMilestoneDetails != null) {
                if (layoutMilestoneDetails.getVisibility() == View.VISIBLE) {
                    layoutMilestoneDetails.setVisibility(View.GONE);
                    if (btnToggleIntimacyCard != null) btnToggleIntimacyCard.setRotation(0f);
                } else {
                    layoutMilestoneDetails.setVisibility(View.VISIBLE);
                    if (btnToggleIntimacyCard != null) btnToggleIntimacyCard.setRotation(180f);
                }
            }
        };

        if (btnToggleIntimacyCard != null) btnToggleIntimacyCard.setOnClickListener(toggleIntimacyListener);
        View rowTitle = findViewById(R.id.rowIntimacyTitle);
        if (rowTitle != null) rowTitle.setOnClickListener(toggleIntimacyListener);

        // Nút Gọi thoại -> Mở ChatCallActivity (Voice Call)
        View.OnClickListener voiceCallListener = v -> {
            int intimacyScore = Math.min(mySentCount, partnerSentCount);
            if (intimacyScore >= 20 && remote) {
                startRemoteCall(false);
            } else if (intimacyScore >= 20) {
                Intent intent = new Intent(ChatDetailActivity.this, ChatCallActivity.class);
                intent.putExtra(ChatCallActivity.EXTRA_NAME, targetName);
                intent.putExtra(ChatCallActivity.EXTRA_PHOTO_RES_ID, targetPhotoResId != 0 ? targetPhotoResId : R.drawable.discovery_photo_1);
                intent.putExtra(ChatCallActivity.EXTRA_IS_VIDEO_CALL, false);
                startActivity(intent);
            } else {
                Toast.makeText(this, "🔒 Cần gửi đủ 20 tin nhắn mỗi người để mở khóa Gọi thoại! (Hiện tại: " + intimacyScore + "/20)", Toast.LENGTH_LONG).show();
            }
        };
        if (btnVoiceCallContainer != null) btnVoiceCallContainer.setOnClickListener(voiceCallListener);
        if (btnVoiceCall != null) btnVoiceCall.setOnClickListener(voiceCallListener);

        // Nút Gọi Video -> Mở ChatCallActivity (Video Call)
        View.OnClickListener videoCallListener = v -> {
            int intimacyScore = Math.min(mySentCount, partnerSentCount);
            if (intimacyScore >= 20 && remote) {
                startRemoteCall(true);
            } else if (intimacyScore >= 20) {
                Intent intent = new Intent(ChatDetailActivity.this, ChatCallActivity.class);
                intent.putExtra(ChatCallActivity.EXTRA_NAME, targetName);
                intent.putExtra(ChatCallActivity.EXTRA_PHOTO_RES_ID, targetPhotoResId != 0 ? targetPhotoResId : R.drawable.discovery_photo_1);
                intent.putExtra(ChatCallActivity.EXTRA_IS_VIDEO_CALL, true);
                startActivity(intent);
            } else {
                Toast.makeText(this, "🔒 Cần gửi đủ 20 tin nhắn mỗi người để mở khóa Gọi Video! (Hiện tại: " + intimacyScore + "/20)", Toast.LENGTH_LONG).show();
            }
        };
        if (btnVideoCallContainer != null) btnVideoCallContainer.setOnClickListener(videoCallListener);
        if (btnVideoCall != null) btnVideoCall.setOnClickListener(videoCallListener);

        // Nút Gửi ảnh/Video -> Truy cập album thiết bị
        View.OnClickListener attachPhotoListener = v -> {
            int intimacyScore = Math.min(mySentCount, partnerSentCount);
            if (intimacyScore >= 10) {
                photoPickerLauncher.launch("image/*");
            } else {
                int remain = 10 - intimacyScore;
                Toast.makeText(this, "🔒 Cần thêm " + remain + " tin nhắn nữa từ bên ít hơn để mở khóa Gửi ảnh!", Toast.LENGTH_LONG).show();
            }
        };
        if (btnAttachPhotoContainer != null) btnAttachPhotoContainer.setOnClickListener(attachPhotoListener);
        if (btnAttachPhoto != null) btnAttachPhoto.setOnClickListener(attachPhotoListener);

        // Nút Gửi tin nhắn
        findViewById(R.id.btnSendMessage).setOnClickListener(v -> sendTextMessage());
    }

    private void setupSampleMessages() {
        if (layoutMessagesList == null) return;
        layoutMessagesList.removeAllViews();

        int intimacyScore = Math.min(mySentCount, partnerSentCount);

        if (intimacyScore >= 20) {
            // LEVEL 3: ĐÃ THÂN THIẾT
            addReceivedBubble("Sounds like a plan! See you at 7.");
            addSentBubble("Perfect, I'll send you the address in a minute.");
            addSystemNoticePill("♥ Hai bạn đã đủ độ thân thiết. Gọi thoại, video call và gửi ảnh đã mở khóa.");
            addSamplePhotoPreview();
            addReceivedBubble("Love the spot! Call you in 5 minutes?");
        } else if (intimacyScore >= 10) {
            // LEVEL 2: THÂN THIẾT MỚI ĐẠT 1 NỬA (10-19 tin)
            addReceivedBubble("Hi Jake, how are you? I saw on the app that we've crossed paths several times this week");
            addSentBubble("Haha truly! Nice to meet you " + targetName + "! What about a cup of coffee today evening?");
            addReceivedBubble("Sure, let's do it!");
            addSentBubble("Great! I will write later the exact time and place. See you soon!");
            int remain = 20 - intimacyScore;
            String lesserSide = (mySentCount < partnerSentCount) ? "Bạn" : targetName;
            addSystemNoticePill("Bạn " + mySentCount + "/20 · " + targetName + " " + partnerSentCount + "/20. " + lesserSide + " cần thêm " + remain + " tin để mở gọi & video");
        } else {
            // LEVEL 1: CHƯA NHẮN GÌ / MỚI NHẮN ÍT (< 10 tin)
            addReceivedBubble("Hi Jake! Rất vui được làm quen với bạn trên Date Zone.");
            addSentBubble("Chào " + targetName + "! Ngày hôm nay của bạn thế nào?");
            int remain10 = 10 - intimacyScore;
            int remain20 = 20 - intimacyScore;
            String lesserSide = (mySentCount < partnerSentCount) ? "Bạn" : targetName;
            addSystemNoticePill("Bạn " + mySentCount + "/20 · " + targetName + " " + partnerSentCount + "/20.\n" + lesserSide + " cần thêm " + remain10 + " tin để mở gửi ảnh, " + remain20 + " tin để mở gọi & video");
        }
    }

    private void addSentBubble(String messageText) {
        TextView bubble = new TextView(this);
        bubble.setText(messageText);
        bubble.setTextColor(ContextCompat.getColor(this, R.color.white));
        bubble.setTextSize(14f);
        bubble.setPadding(28, 20, 28, 20);
        bubble.setBackgroundResource(R.drawable.bg_chat_bubble_sent);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.END;
        params.setMargins(100, 10, 0, 10);
        bubble.setLayoutParams(params);

        layoutMessagesList.addView(bubble);
        scrollToBottom();
    }

    private void addReceivedBubble(String messageText) {
        TextView bubble = new TextView(this);
        bubble.setText(messageText);
        bubble.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        bubble.setTextSize(14f);
        bubble.setPadding(28, 20, 28, 20);
        bubble.setBackgroundResource(R.drawable.bg_chat_bubble_received);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.gravity = Gravity.START;
        bubbleParams.setMargins(0, 10, 100, 10);

        bubble.setLayoutParams(bubbleParams);
        bubble.setOnClickListener(v -> openPartnerProfile());

        layoutMessagesList.addView(bubble);
        scrollToBottom();
    }

    private void addSystemNoticePill(String noticeText) {
        TextView notice = new TextView(this);
        notice.setText(noticeText);
        notice.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        notice.setTextSize(12f);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(32, 16, 32, 16);
        notice.setBackgroundResource(R.drawable.bg_warning_pink);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20, 16, 20, 16);
        notice.setLayoutParams(params);

        layoutMessagesList.addView(notice);
        scrollToBottom();
    }

    private void addSamplePhotoPreview() {
        ImageView photoView = new ImageView(this);
        photoView.setImageResource(targetPhotoResId != 0 ? targetPhotoResId : R.drawable.discovery_photo_2);
        photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(480, 320);
        params.gravity = Gravity.END;
        params.setMargins(100, 12, 0, 12);
        photoView.setLayoutParams(params);

        layoutMessagesList.addView(photoView);
        scrollToBottom();
    }

    private void sendTextMessage() {
        String text = edtChatMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        if (remote) { sendRemoteText(text); return; }

        addSentBubble(text);
        edtChatMessage.setText("");

        mySentCount++;
        checkAndTriggerDialogs();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            addReceivedBubble("Cảm ơn câu trả lời của bạn! ❤️");
            partnerSentCount++;
            checkAndTriggerDialogs();
        }, 1500);
    }

    private void checkAndTriggerDialogs() {
        int intimacyScore = Math.min(mySentCount, partnerSentCount);

        if (intimacyScore >= 20 && !isIntimacyCompletedDialogShown) {
            isIntimacyCompletedDialogShown = true;
            IntimacyCompletedDialog dialog = new IntimacyCompletedDialog(
                    this,
                    () -> updateIntimacyUI()
            );
            dialog.show();
        } else if (intimacyScore >= 10 && !isPhotoUnlockedDialogShown) {
            isPhotoUnlockedDialogShown = true;
            PhotoUnlockedDialog dialog = new PhotoUnlockedDialog(
                    this,
                    () -> updateIntimacyUI()
            );
            dialog.show();
        } else {
            updateIntimacyUI();
        }
    }

    private void sendPhotoOrVideoMessage(Uri mediaUri) {
        if (remote) { sendRemotePhoto(mediaUri); return; }
        String type = getContentResolver().getType(mediaUri);
        boolean isVideo = type != null && type.startsWith("video");

        FrameLayout mediaFrame = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(480, 320);
        params.gravity = Gravity.END;
        params.setMargins(100, 12, 0, 12);
        mediaFrame.setLayoutParams(params);

        ImageView photoView = new ImageView(this);
        photoView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        try {
            photoView.setImageURI(mediaUri);
        } catch (Exception e) {
            photoView.setImageResource(R.drawable.discovery_photo_2);
        }
        mediaFrame.addView(photoView);

        if (isVideo) {
            ImageView playIcon = new ImageView(this);
            FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(48, 48);
            playParams.gravity = Gravity.CENTER;
            playIcon.setLayoutParams(playParams);
            playIcon.setImageResource(R.drawable.ic_videocam);
            playIcon.setColorFilter(Color.WHITE);
            playIcon.setBackgroundResource(R.drawable.bg_circle_add);
            playFrameOverlay(playIcon);
            mediaFrame.addView(playIcon);

            Toast.makeText(this, "Đã gửi Video từ album thành công!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Đã gửi Ảnh từ album thành công!", Toast.LENGTH_SHORT).show();
        }

        layoutMessagesList.addView(mediaFrame);
        scrollToBottom();

        mySentCount++;
        checkAndTriggerDialogs();
    }

    private void playFrameOverlay(ImageView view) {
        if (view != null) {
            view.setPadding(8, 8, 8, 8);
        }
    }

    private void openPartnerProfile() {
        if (remote) {
            if (partnerJson == null) return;
            Intent profile = new Intent(ChatDetailActivity.this, ProfileObject2Activity.class);
            profile.putExtra(ProfileObject2Activity.EXTRA_REMOTE_PROFILE, partnerJson.toString());
            profile.putExtra(ProfileObject2Activity.EXTRA_VIEW_ONLY, true);
            startActivity(profile);
            return;
        }
        Intent intent = new Intent(ChatDetailActivity.this, ProfileObjectActivity.class);
        intent.putExtra(ProfileObjectActivity.EXTRA_NAME, targetName);
        intent.putExtra(ProfileObjectActivity.EXTRA_AGE, targetAge + " tuổi");
        intent.putExtra(ProfileObjectActivity.EXTRA_DETAILS, "Chiều cao 168 cm  Cân nặng 52 kg  Giới tính Nữ");
        intent.putExtra(ProfileObjectActivity.EXTRA_LOCATION, "Thành phố Hồ Chí Minh  Cách bạn 1,8 km");
        intent.putExtra(ProfileObjectActivity.EXTRA_PHOTOS, new int[]{targetPhotoResId != 0 ? targetPhotoResId : R.drawable.discovery_photo_1, R.drawable.discovery_photo_2, R.drawable.discovery_photo_3});
        startActivity(intent);
    }

    private void scrollToBottom() {
        if (scrollMessages != null) {
            scrollMessages.post(() -> scrollMessages.fullScroll(View.FOCUS_DOWN));
        }
    }
}
