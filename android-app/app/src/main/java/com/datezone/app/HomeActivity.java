package com.datezone.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.datezone.app.api.ApiClient;
import com.datezone.app.api.RemoteProfile;
import com.datezone.app.api.RemoteImages;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
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

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    public static class ProfileModel {
        final RemoteProfile remote;
        final String name, age, details, distance;
        final int[] photoResIds;
        ProfileModel(RemoteProfile remote) {
            this.remote = remote;
            name = remote.name;
            age = remote.age;
            details = remote.details;
            distance = remote.distance;
            photoResIds = new int[Math.max(1, remote.photos.length)];
            java.util.Arrays.fill(photoResIds, R.drawable.discovery_profile_placeholder);
        }
    }

    private ApiClient api;
    private String searchQuery = "";
    private boolean loading;
    private ActivityResultLauncher<Intent> filtersLauncher;
    private ActivityResultLauncher<Intent> profileLauncher;

    private CardView cardTop;
    private CardView cardBackground;

    private ImageView ivPhotoTop;
    private ImageView ivPhotoBg;
    private LinearLayout layoutPhotoIndicators;

    private TextView tvNameTop, tvAgeTop, tvDetailsTop, tvDistanceTop;
    private TextView tvNameBg, tvAgeBg, tvDetailsBg, tvDistanceBg;

    private ImageView ivPassOverlay, ivLikeOverlay, ivSuperLikeOverlay;

    private final List<ProfileModel> profileList = new ArrayList<>();
    private int currentProfileIndex = 0;
    private int currentPhotoIndex = 0;

    private float touchStartX, touchStartY;
    private long touchDownTime;
    private int screenWidth;

    private boolean isAnimating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.discovery_activity_home);

        View root = findViewById(R.id.discoveryRoot);
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

                // The root already reserves this space; children must not add it again.
                return insets.inset(bars.left, bars.top, bars.right, bars.bottom);
            });
            ViewCompat.requestApplyInsets(root);
        }

        screenWidth = getResources().getDisplayMetrics().widthPixels;

        initViews();
        api = new ApiClient(this);
        if (savedInstanceState != null) searchQuery = savedInstanceState.getString("searchQuery", "");
        filtersLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                searchQuery = result.getData().getStringExtra(FiltersActivity.EXTRA_SEARCH_QUERY);
                if (searchQuery == null) searchQuery = "";
                loadProfiles();
            }
        });
        profileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null || currentProfileIndex >= profileList.size()) return;
            if (result.getData().getLongExtra("targetId", -1) != profileList.get(currentProfileIndex).remote.id) return;
            boolean like = ProfileObject2Activity.ACTION_LIKE.equals(result.getData().getStringExtra(ProfileObject2Activity.EXTRA_ACTION));
            executeSwipeAnimation(like ? 1200f : -1200f, 0, like ? 20f : -20f, like ? "Thích" : "Bỏ qua");
        });
        displayCurrentProfiles();
        setupCardSwipeGesture();
        setupActionButtons();
        setupBottomNavigation();
        loadProfiles();
    }

    private void initViews() {
        cardTop = findViewById(R.id.cardDiscoveryProfileTop);
        cardBackground = findViewById(R.id.cardDiscoveryProfileBackground);

        ivPhotoTop = findViewById(R.id.ivDiscoveryPhoto);
        ivPhotoBg = findViewById(R.id.ivDiscoveryPhotoBg);
        layoutPhotoIndicators = findViewById(R.id.discoveryPhotoIndicators);

        tvNameTop = findViewById(R.id.tvDiscoveryName);
        tvAgeTop = findViewById(R.id.tvDiscoveryAge);
        tvDetailsTop = findViewById(R.id.tvDiscoveryDetails);
        tvDistanceTop = findViewById(R.id.tvDiscoveryDistance);

        tvNameBg = findViewById(R.id.tvDiscoveryNameBg);
        tvAgeBg = findViewById(R.id.tvDiscoveryAgeBg);
        tvDetailsBg = findViewById(R.id.tvDiscoveryDetailsBg);
        tvDistanceBg = findViewById(R.id.tvDiscoveryDistanceBg);

        ivPassOverlay = findViewById(R.id.ivPassOverlay);
        ivLikeOverlay = findViewById(R.id.ivLikeOverlay);
        ivSuperLikeOverlay = findViewById(R.id.ivSuperLikeOverlay);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("searchQuery", searchQuery);
        super.onSaveInstanceState(outState);
    }

    private void loadProfiles() {
        if (loading || isAnimating) return;
        loading = true;
        profileList.clear();
        currentProfileIndex = 0;
        displayCurrentProfiles();
        tvNameTop.setText("Đang tải hồ sơ…");
        tvDetailsTop.setText("");
        tvDistanceTop.setText("");
        String path = searchQuery.isEmpty() ? "home/feed?limit=20" : "home/search?" + searchQuery;
        api.request(this, "GET", path, null, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                JSONArray data = response.getJSONArray("data");
                List<ProfileModel> loaded = new ArrayList<>();
                for (int i = 0; i < data.length(); i++) loaded.add(new ProfileModel(new RemoteProfile(data.getJSONObject(i))));
                profileList.addAll(loaded);
                loading = false;
                displayCurrentProfiles();
                JSONObject meta = response.optJSONObject("meta");
                if (meta != null && !meta.isNull("expanded"))
                    Toast.makeText(HomeActivity.this, response.optString("message"), Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String message) {
                loading = false;
                displayCurrentProfiles();
                tvNameTop.setText("Chưa tải được hồ sơ");
                tvDetailsTop.setText(message);
                tvDistanceTop.setText("Chạm vào thẻ để thử lại");
            }
        });
    }

    private void displayCurrentProfiles() {
        currentPhotoIndex = 0;

        if (currentProfileIndex < profileList.size()) {
            ProfileModel topProfile = profileList.get(currentProfileIndex);
            tvNameTop.setText(topProfile.name);
            tvAgeTop.setText(topProfile.age);
            tvDetailsTop.setText(topProfile.details);
            tvDistanceTop.setText(topProfile.distance);

            updatePhotoAndIndicators(topProfile);
        } else {
            tvNameTop.setText("Hết hồ sơ phù hợp");
            tvAgeTop.setText("");
            tvDetailsTop.setText("Hãy mở rộng bán kính tìm kiếm hoặc thay đổi bộ lọc.");
            tvDistanceTop.setText("");
            if (ivPhotoTop != null) RemoteImages.load(ivPhotoTop, null);
            if (layoutPhotoIndicators != null) layoutPhotoIndicators.removeAllViews();
        }

        int bgIndex = currentProfileIndex + 1;
        if (bgIndex < profileList.size()) {
            ProfileModel bgProfile = profileList.get(bgIndex);
            tvNameBg.setText(bgProfile.name);
            tvAgeBg.setText(bgProfile.age);
            tvDetailsBg.setText(bgProfile.details);
            tvDistanceBg.setText(bgProfile.distance);
            if (ivPhotoBg != null && bgProfile.photoResIds != null && bgProfile.photoResIds.length > 0) {
                RemoteImages.load(ivPhotoBg, bgProfile.remote.photos.length == 0 ? null : bgProfile.remote.photos[0]);
            }
            cardBackground.setVisibility(View.VISIBLE);
        } else {
            cardBackground.setVisibility(View.GONE);
        }

        // Reset trạng thái thẻ trên cùng
        cardTop.setTranslationX(0);
        cardTop.setTranslationY(0);
        cardTop.setRotation(0);
        cardTop.setAlpha(1f);

        cardBackground.setScaleX(0.95f);
        cardBackground.setScaleY(0.95f);
        cardBackground.setTranslationY(12f);

        hideAllOverlays();
    }

    private void updatePhotoAndIndicators(ProfileModel profile) {
        if (profile == null || profile.photoResIds == null || profile.photoResIds.length == 0) {
            return;
        }

        if (currentPhotoIndex < 0) currentPhotoIndex = 0;
        if (currentPhotoIndex >= profile.photoResIds.length) {
            currentPhotoIndex = profile.photoResIds.length - 1;
        }

        if (ivPhotoTop != null) {
            RemoteImages.load(ivPhotoTop, profile.remote.photos.length == 0 ? null : profile.remote.photos[currentPhotoIndex]);
        }

        if (layoutPhotoIndicators != null) {
            layoutPhotoIndicators.removeAllViews();
            int totalPhotos = profile.photoResIds.length;
            int dotSizePx = (int) (7 * getResources().getDisplayMetrics().density);
            int marginPx = (int) (4 * getResources().getDisplayMetrics().density);

            int mutedColor = ContextCompat.getColor(this, R.color.discovery_muted);

            for (int i = 0; i < totalPhotos; i++) {
                View dot = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSizePx, dotSizePx);
                params.setMargins(marginPx, 0, marginPx, 0);
                dot.setLayoutParams(params);

                if (i == currentPhotoIndex) {
                    dot.setBackgroundResource(R.drawable.bg_circle_add);
                } else {
                    dot.setBackgroundResource(R.drawable.bg_circle_remove);
                    dot.setBackgroundTintList(ColorStateList.valueOf(mutedColor));
                }
                layoutPhotoIndicators.addView(dot);
            }
        }
    }

    private void hideAllOverlays() {
        if (ivPassOverlay != null) ivPassOverlay.setVisibility(View.GONE);
        if (ivLikeOverlay != null) ivLikeOverlay.setVisibility(View.GONE);
        if (ivSuperLikeOverlay != null) ivSuperLikeOverlay.setVisibility(View.GONE);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupCardSwipeGesture() {
        cardTop.setOnTouchListener((view, event) -> {
            if (loading || isAnimating) return true;
            if (currentProfileIndex >= profileList.size()) {
                if (event.getAction() == MotionEvent.ACTION_UP) loadProfiles();
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    touchDownTime = System.currentTimeMillis();
                    cardTop.bringToFront();
                    hideAllOverlays();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float currentRawX = event.getRawX();
                    float currentRawY = event.getRawY();

                    float deltaX = currentRawX - touchStartX;
                    float deltaY = currentRawY - touchStartY;

                    view.setTranslationX(deltaX);
                    view.setTranslationY(deltaY);

                    float rotation = (deltaX / screenWidth) * 15f;
                    view.setRotation(rotation);

                    float dragProgress = Math.min(1f, Math.abs(deltaX) / (screenWidth * 0.5f));
                    cardBackground.setScaleX(0.95f + (0.05f * dragProgress));
                    cardBackground.setScaleY(0.95f + (0.05f * dragProgress));

                    if (deltaY < -120 && Math.abs(deltaX) < 120) {
                        hideAllOverlays();
                        ivSuperLikeOverlay.setVisibility(View.VISIBLE);
                        ivSuperLikeOverlay.setAlpha(Math.min(1f, Math.abs(deltaY) / 250f));
                    } else if (deltaX > 80) {
                        hideAllOverlays();
                        ivLikeOverlay.setVisibility(View.VISIBLE);
                        ivLikeOverlay.setAlpha(Math.min(1f, deltaX / 250f));
                    } else if (deltaX < -80) {
                        hideAllOverlays();
                        ivPassOverlay.setVisibility(View.VISIBLE);
                        ivPassOverlay.setAlpha(Math.min(1f, Math.abs(deltaX) / 250f));
                    } else {
                        hideAllOverlays();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    cardTop.setTranslationX(0);
                    cardTop.setTranslationY(0);
                    cardTop.setRotation(0);
                    cardBackground.setScaleX(0.95f);
                    cardBackground.setScaleY(0.95f);
                    hideAllOverlays();
                    return true;

                case MotionEvent.ACTION_UP:
                    float finalDeltaX = event.getRawX() - touchStartX;
                    float finalDeltaY = event.getRawY() - touchStartY;
                    double moveDistance = Math.hypot(finalDeltaX, finalDeltaY);
                    long pressDuration = System.currentTimeMillis() - touchDownTime;

                    if (moveDistance < 25 && pressDuration < 300) {
                        int cardWidth = cardTop.getWidth();
                        float clickX = event.getX();

                        ProfileModel currentProfile = profileList.get(currentProfileIndex);
                        if (currentProfile.photoResIds != null && currentProfile.photoResIds.length > 0) {
                            if (clickX > cardWidth / 2f) {
                                if (currentPhotoIndex < currentProfile.photoResIds.length - 1) {
                                    currentPhotoIndex++;
                                    updatePhotoAndIndicators(currentProfile);
                                }
                            } else {
                                if (currentPhotoIndex > 0) {
                                    currentPhotoIndex--;
                                    updatePhotoAndIndicators(currentProfile);
                                }
                            }
                        }

                        cardTop.setTranslationX(0);
                        cardTop.setTranslationY(0);
                        cardTop.setRotation(0);
                        hideAllOverlays();
                        return true;
                    }

                    if (finalDeltaY < -250 && Math.abs(finalDeltaX) < 180) {
                        executeSwipeAnimation(0, -1200f, 0f, "Siêu thích");
                    } else if (finalDeltaX > 250) {
                        executeSwipeAnimation(1200f, 0, 20f, "Thích");
                    } else if (finalDeltaX < -250) {
                        executeSwipeAnimation(-1200f, 0, -20f, "Bỏ qua");
                    } else {
                        cardTop.animate()
                                .translationX(0)
                                .translationY(0)
                                .rotation(0)
                                .setDuration(200)
                                .start();

                        cardBackground.animate()
                                .scaleX(0.95f)
                                .scaleY(0.95f)
                                .setDuration(200)
                                .start();

                        hideAllOverlays();
                    }
                    return true;
            }
            return false;
        });
    }

    private void executeSwipeAnimation(float targetX, float targetY, float targetRotation, String actionText) {
        if (isAnimating || loading || currentProfileIndex >= profileList.size()) return;
        isAnimating = true;
        ProfileModel profile = profileList.get(currentProfileIndex);
        String action = "Bỏ qua".equals(actionText) ? "pass" : "Siêu thích".equals(actionText) ? "superlike" : "like";
        JSONObject body = new JSONObject();
        try { body.put("targetId", profile.remote.id).put("action", action); }
        catch (org.json.JSONException e) { isAnimating = false; return; }
        api.request(this, "POST", "home/swipe", body, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                boolean matched = response.getJSONObject("data").getBoolean("matched");
                animateAcceptedSwipe(targetX, targetY, targetRotation, actionText, matched);
            }
            @Override public void onError(String message) {
                isAnimating = false;
                cardTop.setTranslationX(0);
                cardTop.setTranslationY(0);
                cardTop.setRotation(0);
                cardBackground.setScaleX(0.95f);
                cardBackground.setScaleY(0.95f);
                hideAllOverlays();
                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void animateAcceptedSwipe(float targetX, float targetY, float targetRotation, String actionText, boolean matched) {
        isAnimating = true;

        if ("Bỏ qua".equals(actionText)) {
            hideAllOverlays();
            ivPassOverlay.setVisibility(View.VISIBLE);
            ivPassOverlay.setAlpha(1f);
        } else if ("Thích".equals(actionText)) {
            hideAllOverlays();
            ivLikeOverlay.setVisibility(View.VISIBLE);
            ivLikeOverlay.setAlpha(1f);
        } else if ("Siêu thích".equals(actionText)) {
            hideAllOverlays();
            ivSuperLikeOverlay.setVisibility(View.VISIBLE);
            ivSuperLikeOverlay.setAlpha(1f);
        }

        float exitX = targetX != 0 ? (targetX > 0 ? screenWidth * 1.6f : -screenWidth * 1.6f) : 0f;
        float exitY = targetY != 0 ? (targetY < 0 ? -screenWidth * 2f : screenWidth * 2f) : targetY;

        cardTop.bringToFront();
        cardTop.animate()
                .translationX(exitX)
                .translationY(exitY)
                .rotation(targetRotation)
                .setDuration(280)
                .withEndAction(() -> {
                    if (matched) {
                        ProfileModel currentProfile = (currentProfileIndex < profileList.size()) ? profileList.get(currentProfileIndex) : null;
                        String matchedName = currentProfile != null ? currentProfile.name : "Đối tượng";
                        int matchedPhoto = (currentProfile != null && currentProfile.photoResIds != null && currentProfile.photoResIds.length > 0) ? currentProfile.photoResIds[0] : R.drawable.discovery_photo_1;

                        MatchSuccessDialog matchDialog = new MatchSuccessDialog(
                                HomeActivity.this,
                                matchedName,
                                currentProfile != null && currentProfile.remote.photos.length > 0 ? currentProfile.remote.photos[0] : "",
                                new MatchSuccessDialog.OnMatchActionListener() {
                                    @Override
                                    public void onSayHello(String targetName) {
                                        android.content.Intent intent = new android.content.Intent(HomeActivity.this, ChatDetailActivity.class);
                                        intent.putExtra(ChatDetailActivity.EXTRA_NAME, targetName);
                                        intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_RES_ID, matchedPhoto);
                                        intent.putExtra(ChatDetailActivity.EXTRA_INITIAL_MSG_COUNT, 0);
                                        startActivity(intent);
                                    }

                                    @Override
                                    public void onContinue() {}
                                }
                        );
                        matchDialog.show();
                    } else {
                        Toast.makeText(this, actionText + " thành công!", Toast.LENGTH_SHORT).show();
                    }
                    currentProfileIndex++;
                    displayCurrentProfiles();
                    isAnimating = false;
                })
                .start();

        cardBackground.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(280)
                .start();
    }

    private void setupActionButtons() {
        findViewById(R.id.btnDiscoveryPass).setOnClickListener(v -> {
            if (!isAnimating && currentProfileIndex < profileList.size()) {
                executeSwipeAnimation(-1200f, 0, -20f, "Bỏ qua");
            }
        });

        findViewById(R.id.btnDiscoveryLike).setOnClickListener(v -> {
            if (!isAnimating && currentProfileIndex < profileList.size()) {
                executeSwipeAnimation(1200f, 0, 20f, "Thích");
            }
        });

        findViewById(R.id.btnDiscoverySuperLike).setOnClickListener(v -> {
            if (!isAnimating && currentProfileIndex < profileList.size()) {
                executeSwipeAnimation(0, -1200f, 0f, "Siêu thích");
            }
        });

        findViewById(R.id.btnDiscoveryFilters).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(HomeActivity.this, FiltersActivity.class);
            if (!loading && !isAnimating) {
                intent.putExtra(FiltersActivity.EXTRA_SEARCH_QUERY, searchQuery);
                filtersLauncher.launch(intent);
            }
        });

        // Nút Xem chi tiết hồ sơ (Mũi tên chỉ lên ^) -> Mở ProfileObject2Activity
        findViewById(R.id.btnDiscoveryProfileDetails).setOnClickListener(v -> {
            if (!loading && !isAnimating && currentProfileIndex < profileList.size()) {
                ProfileModel profile = profileList.get(currentProfileIndex);
                android.content.Intent intent = new android.content.Intent(HomeActivity.this, ProfileObject2Activity.class);
                intent.putExtra(ProfileObject2Activity.EXTRA_NAME, profile.name);
                intent.putExtra(ProfileObject2Activity.EXTRA_AGE, profile.age);
                intent.putExtra(ProfileObject2Activity.EXTRA_DETAILS, profile.details);
                intent.putExtra(ProfileObject2Activity.EXTRA_LOCATION, profile.distance);
                intent.putExtra(ProfileObject2Activity.EXTRA_REMOTE_PROFILE, profile.remote.json.toString());
                profileLauncher.launch(intent);
            }
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.discoveryBottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navDiscoveryExplore);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.navDiscoveryExplore) {
                    return true;
                } else if (itemId == R.id.navDiscoveryLikes) {
                    android.content.Intent intent = new android.content.Intent(HomeActivity.this, MatchesActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryChat) {
                    android.content.Intent intent = new android.content.Intent(HomeActivity.this, ChatListActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryProfile) {
                    android.content.Intent intent = new android.content.Intent(HomeActivity.this, ProfileHomeActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                }
                return false;
            });
        }
    }
}
