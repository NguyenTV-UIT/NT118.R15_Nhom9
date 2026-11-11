package com.datezone.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ChatCallActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "EXTRA_NAME";
    public static final String EXTRA_PHOTO_RES_ID = "EXTRA_PHOTO_RES_ID";
    public static final String EXTRA_IS_VIDEO_CALL = "EXTRA_IS_VIDEO_CALL";
    /** Có EXTRA_MATCH_ID (> 0): cuộc gọi được đăng ký với máy chủ. EXTRA_CALL_ID: mở lại / nhận 1 cuộc gọi đã có. */
    public static final String EXTRA_MATCH_ID = "EXTRA_MATCH_ID";
    public static final String EXTRA_CALL_ID = "EXTRA_CALL_ID";
    public static final String EXTRA_PHOTO_URL = "EXTRA_PHOTO_URL";
    private static final long STATUS_POLL_MS = 2000;

    private long matchId;
    private long callId;
    private boolean remote;
    private boolean callClosed;
    private String targetPhotoUrl = "";
    private com.datezone.app.api.ApiClient api;
    private final Runnable statusPoll = new Runnable() {
        @Override public void run() { pollCallStatus(); }
    };

    private String targetName = "Nguyên";
    private int targetPhotoResId = R.drawable.discovery_photo_1;
    private boolean isVideoCall = false;

    private ImageView ivCallBackground;
    private ImageView ivCallAvatar;
    private TextView tvCallPartnerName;
    private TextView tvCallStatus;

    private ImageButton btnMinimizeCall;
    private ImageButton btnSwitchCamera;

    private ImageButton btnSpeaker;
    private ImageButton btnToggleVideo;
    private ImageButton btnToggleMic;
    private ImageButton btnEndCall;

    private CardView cardFloatingPip;
    private ImageView ivPipAvatar;
    private TextView tvPipTimer;
    private View layoutCallCenterInfo;
    private View layoutCallActionButtons;

    // Loa có 3 mức: 0: Lớn (Trắng), 1: Nhỏ (Trong), 2: Tắt (Gạch chéo)
    private int speakerState = 0;
    private boolean isVideoOn = true;
    private boolean isMicOn = true;
    private boolean isFrontCamera = true;
    private boolean isCallConnected = false;

    private int secondsElapsed = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private float dX, dY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.chat_activity_call);

        View root = findViewById(R.id.chatCallRoot);
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
            if (intent.hasExtra(EXTRA_PHOTO_RES_ID)) {
                targetPhotoResId = intent.getIntExtra(EXTRA_PHOTO_RES_ID, R.drawable.discovery_photo_1);
            }
            if (intent.hasExtra(EXTRA_IS_VIDEO_CALL)) {
                isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false);
            }
            matchId = intent.getLongExtra(EXTRA_MATCH_ID, 0);
            callId = intent.getLongExtra(EXTRA_CALL_ID, 0);
            String url = intent.getStringExtra(EXTRA_PHOTO_URL);
            targetPhotoUrl = url == null ? "" : url;
        }
        remote = matchId > 0;
        if (remote) api = new com.datezone.app.api.ApiClient(this);

        initViews();
        setupCallData();
        setupListeners();
        startCallTimer();
    }

    private void initViews() {
        ivCallBackground = findViewById(R.id.ivCallBackground);
        ivCallAvatar = findViewById(R.id.ivCallAvatar);
        tvCallPartnerName = findViewById(R.id.tvCallPartnerName);
        tvCallStatus = findViewById(R.id.tvCallStatus);

        btnMinimizeCall = findViewById(R.id.btnMinimizeCall);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);

        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnToggleVideo = findViewById(R.id.btnToggleVideo);
        btnToggleMic = findViewById(R.id.btnToggleMic);
        btnEndCall = findViewById(R.id.btnEndCall);

        cardFloatingPip = findViewById(R.id.cardFloatingPip);
        ivPipAvatar = findViewById(R.id.ivPipAvatar);
        tvPipTimer = findViewById(R.id.tvPipTimer);
        layoutCallCenterInfo = findViewById(R.id.layoutCallCenterInfo);
        layoutCallActionButtons = findViewById(R.id.layoutCallActionButtons);
    }

    private void setupCallData() {
        if (tvCallPartnerName != null) tvCallPartnerName.setText(targetName);
        if (ivCallAvatar != null) ivCallAvatar.setImageResource(targetPhotoResId);
        if (ivCallBackground != null) ivCallBackground.setImageResource(targetPhotoResId);
        if (ivPipAvatar != null) ivPipAvatar.setImageResource(targetPhotoResId);

        ActiveCallManager.getInstance().startCall(targetName, targetPhotoResId, isVideoCall);
        if (remote) {
            ActiveCallManager.getInstance().setRemoteCall(matchId, callId, targetPhotoUrl);
            if (!targetPhotoUrl.isEmpty()) {
                if (ivCallAvatar != null) com.datezone.app.api.RemoteImages.load(ivCallAvatar, targetPhotoUrl);
                if (ivCallBackground != null) com.datezone.app.api.RemoteImages.load(ivCallBackground, targetPhotoUrl);
                if (ivPipAvatar != null) com.datezone.app.api.RemoteImages.load(ivPipAvatar, targetPhotoUrl);
            }
        }

        if (isVideoCall) {
            isVideoOn = true;
        } else {
            isVideoOn = false;
        }

        updateSpeakerUI(false);
        updateVideoUI(false);
        updateMicUI();
    }

    private void startCallTimer() {
        tvCallStatus.setText("Contacting...");

        // Giả lập sau 2 giây bắt đầu thông suốt cuộc gọi
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCallConnected) {
                    isCallConnected = true;
                    secondsElapsed = 0;
                }

                int mins = secondsElapsed / 60;
                int secs = secondsElapsed % 60;
                String timeStr = String.format("%02d:%02d", mins, secs);

                if (tvCallStatus != null) tvCallStatus.setText(timeStr);
                if (tvPipTimer != null) tvPipTimer.setText(timeStr);

                secondsElapsed++;
                timerHandler.postDelayed(this, 1000);
            }
        };

        if (remote) {
            // Cuộc gọi thật: chỉ bắt đầu đếm giờ khi máy chủ báo người kia đã nhấc máy
            tvCallStatus.setText("Đang kết nối…");
            if (callId > 0) pollCallStatus();
            else startRemoteCall();
            return;
        }

        timerHandler.postDelayed(timerRunnable, 2000);
    }

    // ═══════════════ CUỘC GỌI ĐĂNG KÝ VỚI MÁY CHỦ ═══════════════

    /** POST /chats/:matchId/calls - bắt đầu đổ chuông bên máy người kia. */
    private void startRemoteCall() {
        api.request(this, "POST", "chats/" + matchId + "/calls",
                com.datezone.app.api.ApiUi.data("type", isVideoCall ? "video" : "voice"),
                new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                        callId = response.getJSONObject("data").getJSONObject("call").getLong("id");
                        ActiveCallManager.getInstance().setRemoteCall(matchId, callId, targetPhotoUrl);
                        if (tvCallStatus != null) tvCallStatus.setText("Đang đổ chuông…");
                        timerHandler.postDelayed(statusPoll, STATUS_POLL_MS);
                    }
                    @Override public void onError(String message) {
                        closeRemoteCall(message);
                    }
                });
    }

    /** GET /calls/:callId mỗi 2 giây: nhấc máy -> đếm giờ; từ chối / nhỡ / kết thúc -> đóng màn. */
    private void pollCallStatus() {
        if (callClosed || isFinishing()) return;
        api.request(this, "GET", "calls/" + callId, null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                org.json.JSONObject call = response.getJSONObject("data").getJSONObject("call");
                String status = call.optString("status");
                if ("ringing".equals(status)) {
                    if (tvCallStatus != null) tvCallStatus.setText("Đang đổ chuông…");
                } else if ("accepted".equals(status)) {
                    if (!isCallConnected) {
                        isCallConnected = true;
                        java.util.Date answered = com.datezone.app.api.ChatUi.parseTime(call.optString("answeredAt"));
                        long elapsed = answered == null ? 0 : (System.currentTimeMillis() - answered.getTime()) / 1000;
                        secondsElapsed = (int) Math.max(0, Math.min(elapsed, 24 * 3600));
                        timerHandler.removeCallbacks(timerRunnable);
                        timerHandler.post(timerRunnable);
                    }
                } else {
                    closeRemoteCall("rejected".equals(status) ? targetName + " đã từ chối cuộc gọi"
                            : "missed".equals(status) ? targetName + " không nghe máy"
                            : "Cuộc gọi đã kết thúc");
                    return;
                }
                timerHandler.postDelayed(statusPoll, STATUS_POLL_MS);
            }
            @Override public void onError(String message) {
                // Mất mạng tạm thời: thử lại ở lần hỏi sau
                timerHandler.postDelayed(statusPoll, STATUS_POLL_MS);
            }
        });
    }

    private void closeRemoteCall(String message) {
        if (callClosed) return;
        callClosed = true;
        ActiveCallManager.getInstance().endCall();
        timerHandler.removeCallbacks(statusPoll);
        timerHandler.removeCallbacks(timerRunnable);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void setupListeners() {
        // Nút Thu nhỏ cuộc gọi góc trên bên trái -> Trở về ChatDetail và hiện thanh xanh lá
        if (btnMinimizeCall != null) {
            btnMinimizeCall.setOnClickListener(v -> minimizeCallToChat());
        }

        // Nút Xoay camera (Cam trước / Cam sau)
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setOnClickListener(v -> {
                isFrontCamera = !isFrontCamera;
                String camText = isFrontCamera ? "Cam trước" : "Cam sau";
                Toast.makeText(this, "Đã chuyển sang " + camText, Toast.LENGTH_SHORT).show();
            });
        }

        // Nút Loa (3 mức: Lớn - Nhỏ - Tắt)
        if (btnSpeaker != null) {
            btnSpeaker.setOnClickListener(v -> {
                speakerState = (speakerState + 1) % 3;
                updateSpeakerUI(true);
            });
        }

        // Nút Mở/Tắt Video
        if (btnToggleVideo != null) {
            btnToggleVideo.setOnClickListener(v -> {
                isVideoOn = !isVideoOn;
                updateVideoUI(true);
            });
        }

        // Nút Mở/Tắt Micro
        if (btnToggleMic != null) {
            btnToggleMic.setOnClickListener(v -> {
                isMicOn = !isMicOn;
                updateMicUI();
            });
        }

        // Nút Tắt máy
        if (btnEndCall != null) {
            btnEndCall.setOnClickListener(v -> finishCall());
        }
    }

    private void updateSpeakerUI(boolean showToast) {
        if (btnSpeaker == null) return;

        if (speakerState == 0) {
            btnSpeaker.setBackgroundResource(R.drawable.bg_circle_translucent);
            btnSpeaker.setImageResource(R.drawable.ic_speaker_high);
            btnSpeaker.setColorFilter(Color.WHITE);
            if (showToast) Toast.makeText(this, "Loa ngoài (Lớn)", Toast.LENGTH_SHORT).show();
        } else if (speakerState == 1) {
            btnSpeaker.setBackgroundResource(R.drawable.bg_circle_translucent);
            btnSpeaker.setImageResource(R.drawable.ic_speaker_low);
            btnSpeaker.setColorFilter(Color.WHITE);
            if (showToast) Toast.makeText(this, "Loa trong (Nhỏ)", Toast.LENGTH_SHORT).show();
        } else {
            btnSpeaker.setBackgroundResource(R.drawable.bg_circle_translucent);
            btnSpeaker.setImageResource(R.drawable.ic_speaker_mute);
            btnSpeaker.setColorFilter(Color.WHITE);
            if (showToast) Toast.makeText(this, "Đã tắt loa", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateVideoUI(boolean showToast) {
        if (btnToggleVideo == null) return;

        if (isVideoOn) {
            btnToggleVideo.setBackgroundResource(R.drawable.bg_circle_translucent);
            btnToggleVideo.setImageResource(R.drawable.ic_videocam);
            btnToggleVideo.setColorFilter(Color.WHITE);
            btnToggleVideo.setAlpha(1.0f);

            if (btnSwitchCamera != null) {
                btnSwitchCamera.setVisibility(View.VISIBLE);
            }

            if (showToast) {
                String camText = isFrontCamera ? "Cam trước" : "Cam sau";
                Toast.makeText(this, "Đã bật Camera (" + camText + ")", Toast.LENGTH_SHORT).show();
            }
        } else {
            btnToggleVideo.setBackgroundResource(R.drawable.bg_circle_white);
            btnToggleVideo.setImageResource(R.drawable.ic_video_off);
            btnToggleVideo.setColorFilter(ContextCompat.getColor(this, R.color.text_primary));
            btnToggleVideo.setAlpha(1.0f);

            if (btnSwitchCamera != null) {
                btnSwitchCamera.setVisibility(View.GONE);
            }

            if (showToast) {
                Toast.makeText(this, "Đã tắt Camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateMicUI() {
        if (btnToggleMic == null) return;

        if (isMicOn) {
            btnToggleMic.setBackgroundResource(R.drawable.bg_circle_translucent);
            btnToggleMic.setImageResource(R.drawable.ic_mic_on);
            btnToggleMic.setColorFilter(Color.WHITE);
            btnToggleMic.setAlpha(1.0f);
        } else {
            btnToggleMic.setBackgroundResource(R.drawable.bg_circle_white);
            btnToggleMic.setImageResource(R.drawable.ic_mic_off);
            btnToggleMic.setColorFilter(ContextCompat.getColor(this, R.color.text_primary));
            btnToggleMic.setAlpha(1.0f);
        }
    }

    private void minimizeCallToChat() {
        ActiveCallManager.getInstance().startCall(targetName, targetPhotoResId, isVideoCall);

        Intent intent = new Intent(ChatCallActivity.this, ChatDetailActivity.class);
        intent.putExtra(ChatDetailActivity.EXTRA_NAME, targetName);
        if (remote) {
            ActiveCallManager.getInstance().setRemoteCall(matchId, callId, targetPhotoUrl);
            intent.putExtra(ChatDetailActivity.EXTRA_MATCH_ID, matchId);
            intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_URL, targetPhotoUrl);
        } else {
            intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_RES_ID, targetPhotoResId);
            intent.putExtra(ChatDetailActivity.EXTRA_INITIAL_MSG_COUNT, 20);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void finishCall() {
        if (remote && callId > 0 && !callClosed) {
            // Cúp máy: máy chủ tự hiểu là huỷ (đang đổ chuông) hay kết thúc (đang nói chuyện)
            callClosed = true;
            api.request(this, "POST", "calls/" + callId + "/end", null, new com.datezone.app.api.ApiClient.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) { }
                @Override public void onError(String message) { }
            });
        }
        timerHandler.removeCallbacks(statusPoll);
        ActiveCallManager.getInstance().endCall();
        timerHandler.removeCallbacks(timerRunnable);
        Toast.makeText(this, "Đã kết thúc cuộc gọi", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        timerHandler.removeCallbacks(statusPoll);
    }
}
