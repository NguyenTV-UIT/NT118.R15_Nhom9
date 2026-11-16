package com.datezone.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;

public class ProfileObject2Activity extends AppCompatActivity {
    public static final String EXTRA_REMOTE_PROFILE = "remoteProfile";
    /** true: chỉ xem hồ sơ (mở từ màn chat) - ẩn nút Thích / Bỏ qua. */
    public static final String EXTRA_VIEW_ONLY = "viewOnly";
    private com.datezone.app.api.RemoteProfile remoteProfile;

    public static final String EXTRA_NAME = "EXTRA_NAME";
    public static final String EXTRA_AGE = "EXTRA_AGE";
    public static final String EXTRA_DETAILS = "EXTRA_DETAILS";
    public static final String EXTRA_LOCATION = "EXTRA_LOCATION";
    public static final String EXTRA_BIO = "EXTRA_BIO";
    public static final String EXTRA_INTERESTS = "EXTRA_INTERESTS";
    public static final String EXTRA_PHOTOS = "EXTRA_PHOTOS";

    private View profileContainer;
    private View btnPass;
    private View btnLike;

    private ImageView ivProfileCover;
    private TextView tvProfileNameAge;
    private TextView tvProfileGender;
    private TextView tvProfileLocation;
    private TextView tvProfileBio;

    private ChipGroup chipGroupProfileInterests;

    private ImageView ivGallery1, ivGallery2, ivGallery3, ivGallery4;

    private String currentUserName = "Trương Vĩnh Nguyên";
    private int[] photoResIds = new int[]{R.drawable.discovery_photo_1, R.drawable.discovery_photo_2, R.drawable.discovery_photo_3, R.drawable.discovery_photo_4, R.drawable.discovery_photo_5};

    private float touchStartX, touchStartY;
    private int screenWidth;
    private boolean isAnimating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.discovery_activity_profile_object_2);

        View root = findViewById(R.id.profileDetailRoot2);
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

        screenWidth = getResources().getDisplayMetrics().widthPixels;

        initViews();
        bindDataFromIntent();
        setupListeners();
        setupInteractiveSwipeGesture();
    }

    private void initViews() {
        profileContainer = findViewById(R.id.profileDetailContainer);
        btnPass = findViewById(R.id.btnPass);
        btnLike = findViewById(R.id.btnLike);

        ivProfileCover = findViewById(R.id.ivProfileCover);
        tvProfileNameAge = findViewById(R.id.tvProfileNameAge);
        tvProfileGender = findViewById(R.id.tvProfileGender);
        tvProfileLocation = findViewById(R.id.tvProfileLocation);
        tvProfileBio = findViewById(R.id.tvProfileBio);

        chipGroupProfileInterests = findViewById(R.id.chipGroupProfileInterests);

        ivGallery1 = findViewById(R.id.ivProfileGalleryPhoto1);
        ivGallery2 = findViewById(R.id.ivProfileGalleryPhoto2);
        ivGallery3 = findViewById(R.id.ivProfileGalleryPhoto3);
        ivGallery4 = findViewById(R.id.ivProfileGalleryPhoto4);
    }

    private void bindDataFromIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_REMOTE_PROFILE)) {
            try {
                remoteProfile = new com.datezone.app.api.RemoteProfile(new org.json.JSONObject(intent.getStringExtra(EXTRA_REMOTE_PROFILE)));
                currentUserName = remoteProfile.name;
                tvProfileNameAge.setText(remoteProfile.name + ", " + remoteProfile.age);
                tvProfileGender.setText(remoteProfile.details);
                tvProfileLocation.setText(remoteProfile.distance);
                tvProfileBio.setText(remoteProfile.bio.isEmpty() ? "Chưa có giới thiệu" : remoteProfile.bio);
                populateInterestsChips(remoteProfile.interests);
                findViewById(R.id.tvProfilePersonalInfoTitle).setVisibility(View.GONE);
                ((View) findViewById(R.id.tvProfileGoal).getParent()).setVisibility(View.GONE);
                loadRemotePersonalInfo();
                ImageView[] views = {ivProfileCover, ivGallery1, ivGallery2, ivGallery3, ivGallery4};
                for (int i = 0; i < views.length; i++) {
                    com.datezone.app.api.RemoteImages.load(views[i], i < remoteProfile.photos.length ? remoteProfile.photos[i] : null);
                    if (i > 0) views[i].setVisibility(i < remoteProfile.photos.length ? View.VISIBLE : View.GONE);
                }
                if (intent.getBooleanExtra(EXTRA_VIEW_ONLY, false)) {
                    if (btnPass != null) btnPass.setVisibility(View.GONE);
                    if (btnLike != null) btnLike.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Không đọc được hồ sơ", Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }
        if (intent != null) {
            String name = intent.hasExtra(EXTRA_NAME) ? intent.getStringExtra(EXTRA_NAME) : "Trương Vĩnh Nguyên";
            String age = intent.hasExtra(EXTRA_AGE) ? intent.getStringExtra(EXTRA_AGE) : "20 tuổi";
            String details = intent.hasExtra(EXTRA_DETAILS) ? intent.getStringExtra(EXTRA_DETAILS) : "Chiều cao 170 cm  Cân nặng 65 kg  Giới tính Nam";
            String location = intent.hasExtra(EXTRA_LOCATION) ? intent.getStringExtra(EXTRA_LOCATION) : "Thành phố Hồ Chí Minh  Cách bạn 1,2 km";
            String bio = intent.hasExtra(EXTRA_BIO) ? intent.getStringExtra(EXTRA_BIO) : "Thích du lịch, cà phê và trải nghiệm các địa điểm mới. Mong muốn kết nối với những người bạn tích cực và chân thành.";

            if (name != null && !name.isEmpty()) {
                currentUserName = name;
            }

            if (intent.hasExtra(EXTRA_PHOTOS)) {
                int[] customPhotos = intent.getIntArrayExtra(EXTRA_PHOTOS);
                if (customPhotos != null && customPhotos.length > 0) {
                    photoResIds = customPhotos;
                }
            }

            tvProfileNameAge.setText(currentUserName + ", " + age);
            tvProfileGender.setText(details);
            tvProfileLocation.setText(location);
            tvProfileBio.setText(bio);

            if (photoResIds.length > 0) ivProfileCover.setImageResource(photoResIds[0]);
            if (photoResIds.length > 1) ivGallery1.setImageResource(photoResIds[1]);
            if (photoResIds.length > 2) ivGallery2.setImageResource(photoResIds[2]);
            if (photoResIds.length > 3) ivGallery3.setImageResource(photoResIds[3]);
            if (photoResIds.length > 4) ivGallery4.setImageResource(photoResIds[4]);

            ArrayList<String> interests = intent.getStringArrayListExtra(EXTRA_INTERESTS);
            if (interests == null || interests.isEmpty()) {
                interests = new ArrayList<>();
                interests.add("Du lịch");
                interests.add("Âm nhạc");
                interests.add("Cà phê");
                interests.add("Ẩm thực");
            }
            populateInterestsChips(interests);
        }
    }

    private void populateInterestsChips(ArrayList<String> interests) {
        if (chipGroupProfileInterests == null) return;
        chipGroupProfileInterests.removeAllViews();

        for (String interestText : interests) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_profile_interest_chip, chipGroupProfileInterests, false);
            chip.setText(interestText);
            chipGroupProfileInterests.addView(chip);
        }
    }

    private void setupListeners() {
        // Nút quay lại góc trên bên trái
        findViewById(R.id.btnProfileBack).setOnClickListener(v -> finish());

        // Nút Pass (X bên trái)
        if (btnPass != null) {
            btnPass.setOnClickListener(v -> executePassAction());
        }

        // Nút Like (Trái tim bên phải)
        if (btnLike != null) {
            btnLike.setOnClickListener(v -> executeLikeAction());
        }

        // Xem tất cả ảnh -> Mở PhotoViewerActivity
        View.OnClickListener openPhotoViewerListener = v -> {
            Intent intent = new Intent(ProfileObject2Activity.this, PhotoViewerActivity.class);
            if (remoteProfile != null) intent.putExtra(PhotoViewerActivity.EXTRA_PHOTO_URLS, remoteProfile.photos);
            else intent.putExtra(PhotoViewerActivity.EXTRA_PHOTOS, photoResIds);
            startActivity(intent);
        };

        findViewById(R.id.btnProfileViewAllPhotos).setOnClickListener(openPhotoViewerListener);
        findViewById(R.id.profilePhotoGallery).setOnClickListener(openPhotoViewerListener);
        ivProfileCover.setOnClickListener(openPhotoViewerListener);

        // Nút Chặn người dùng ở cuối trang
        View btnBlock = findViewById(R.id.btnBlockUser);
        if (btnBlock != null) {
            btnBlock.setOnClickListener(v -> showBlockUserDialog());
        }

        // Nút Báo cáo người dùng ở cuối trang
        View btnReport = findViewById(R.id.btnReportUser);
        if (btnReport != null) {
            btnReport.setOnClickListener(v -> showReportUserDialog());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupInteractiveSwipeGesture() {
        if (profileContainer == null) return;

        profileContainer.setOnTouchListener((view, event) -> {
            if (isAnimating) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - touchStartX;
                    float deltaY = event.getRawY() - touchStartY;

                    view.setTranslationX(deltaX);
                    view.setTranslationY(deltaY * 0.3f);
                    view.setRotation((deltaX / screenWidth) * 12f);

                    // Làm mờ toàn trang và hiển thị nổi bật Icon tương ứng (X hoặc Heart)
                    float progress = Math.min(1.0f, Math.abs(deltaX) / (screenWidth * 0.4f));
                    view.setAlpha(Math.max(0.4f, 1.0f - (progress * 0.5f)));

                    if (deltaX < -40) {
                        // Kéo về BÊN TRÁI -> Đậm nút Từ chối X bên trái, nhạt màn hình & nút Thích
                        if (btnPass != null) {
                            btnPass.setAlpha(1.0f);
                            btnPass.setScaleX(1.0f + (progress * 0.25f));
                            btnPass.setScaleY(1.0f + (progress * 0.25f));
                        }
                        if (btnLike != null) {
                            btnLike.setAlpha(Math.max(0.2f, 1.0f - progress));
                            btnLike.setScaleX(1.0f - (progress * 0.15f));
                            btnLike.setScaleY(1.0f - (progress * 0.15f));
                        }
                    } else if (deltaX > 40) {
                        // Kéo về BÊN PHẢI -> Đậm nút Thích Heart bên phải, nhạt màn hình & nút X
                        if (btnLike != null) {
                            btnLike.setAlpha(1.0f);
                            btnLike.setScaleX(1.0f + (progress * 0.25f));
                            btnLike.setScaleY(1.0f + (progress * 0.25f));
                        }
                        if (btnPass != null) {
                            btnPass.setAlpha(Math.max(0.2f, 1.0f - progress));
                            btnPass.setScaleX(1.0f - (progress * 0.15f));
                            btnPass.setScaleY(1.0f - (progress * 0.15f));
                        }
                    } else {
                        resetBottomButtonsState();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float finalDeltaX = event.getRawX() - touchStartX;

                    if (finalDeltaX < -220) {
                        executePassAction();
                    } else if (finalDeltaX > 220) {
                        executeLikeAction();
                    } else {
                        resetProfilePosition();
                    }
                    return true;
            }
            return false;
        });
    }

    private void resetProfilePosition() {
        if (profileContainer != null) {
            profileContainer.animate()
                    .translationX(0)
                    .translationY(0)
                    .rotation(0)
                    .alpha(1.0f)
                    .setDuration(200)
                    .start();
        }
        resetBottomButtonsState();
    }

    private void resetBottomButtonsState() {
        if (btnPass != null) {
            btnPass.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        }
        if (btnLike != null) {
            btnLike.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        }
    }

    public static final String EXTRA_ACTION = "EXTRA_ACTION";
    public static final String ACTION_LIKE = "LIKE";
    public static final String ACTION_PASS = "PASS";

    private void returnRemoteAction(String action) {
        if (isAnimating) return;
        isAnimating = true;
        Intent result = new Intent().putExtra("targetId", remoteProfile.id)
                .putExtra(EXTRA_NAME, remoteProfile.name).putExtra(EXTRA_ACTION, action);
        setResult(RESULT_OK, result);
        finish();
    }

    private void loadRemotePersonalInfo() {
        new com.datezone.app.api.ApiClient(this).publicRequest(this, "GET", "meta/options", null,
                new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                        org.json.JSONObject options = response.getJSONObject("data");
                        String[] keys = {"relationshipGoal", "zodiac", "smoking", "drinking", "exercise", "education"};
                        String[] titles = {"Mục tiêu", "Cung hoàng đạo", "Hút thuốc", "Rượu bia", "Tập luyện", "Học vấn"};
                        int[] ids = {R.id.tvProfileGoal, R.id.tvProfileZodiac, R.id.tvProfileSmoking,
                                R.id.tvProfileDrinking, R.id.tvProfileExercise, R.id.tvProfileEducation};
                        boolean hasInfo = false;
                        for (int i = 0; i < keys.length; i++) {
                            TextView text = findViewById(ids[i]);
                            text.setVisibility(View.GONE);
                            if (remoteProfile.json.isNull(keys[i])) continue;
                            String code = remoteProfile.json.getString(keys[i]);
                            org.json.JSONArray choices = options.optJSONArray(keys[i]);
                            if (choices == null) continue;
                            for (int j = 0; j < choices.length(); j++) {
                                org.json.JSONObject choice = choices.getJSONObject(j);
                                if (code.equals(choice.getString("code"))) {
                                    text.setText(titles[i] + ": " + choice.getString("label"));
                                    text.setVisibility(View.VISIBLE);
                                    hasInfo = true;
                                    break;
                                }
                            }
                        }
                        findViewById(R.id.tvProfilePersonalInfoTitle).setVisibility(hasInfo ? View.VISIBLE : View.GONE);
                        ((View) findViewById(R.id.tvProfileGoal).getParent()).setVisibility(hasInfo ? View.VISIBLE : View.GONE);
                    }
                    @Override public void onError(String message) { /* Keep unavailable fields hidden. */ }
                });
    }

    private void executePassAction() {
        if (remoteProfile != null) { returnRemoteAction(ACTION_PASS); return; }
        if (isAnimating) return;
        isAnimating = true;

        if (btnPass != null) {
            btnPass.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start();
        }

        if (profileContainer != null) {
            profileContainer.animate()
                    .translationX(-screenWidth * 1.5f)
                    .rotation(-20f)
                    .alpha(0.2f)
                    .setDuration(280)
                    .withEndAction(() -> {
                        Toast.makeText(this, "Đã bỏ qua Lượt thích", Toast.LENGTH_SHORT).show();
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(EXTRA_NAME, currentUserName);
                        resultIntent.putExtra(EXTRA_ACTION, ACTION_PASS);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    })
                    .start();
        } else {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_NAME, currentUserName);
            resultIntent.putExtra(EXTRA_ACTION, ACTION_PASS);
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    private void executeLikeAction() {
        if (remoteProfile != null) { returnRemoteAction(ACTION_LIKE); return; }
        if (isAnimating) return;
        isAnimating = true;

        if (btnLike != null) {
            btnLike.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start();
        }

        int mainPhotoResId = (photoResIds != null && photoResIds.length > 0) ? photoResIds[0] : R.drawable.discovery_photo_1;

        if (profileContainer != null) {
            profileContainer.animate()
                    .translationX(screenWidth * 1.5f)
                    .rotation(20f)
                    .alpha(0.2f)
                    .setDuration(280)
                    .withEndAction(() -> {
                        MatchSuccessDialog matchDialog = new MatchSuccessDialog(
                                ProfileObject2Activity.this,
                                currentUserName,
                                mainPhotoResId,
                                new MatchSuccessDialog.OnMatchActionListener() {
                                    @Override
                                    public void onSayHello(String targetName) {
                                        Intent intent = new Intent(ProfileObject2Activity.this, ChatDetailActivity.class);
                                        intent.putExtra(ChatDetailActivity.EXTRA_NAME, targetName);
                                        intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_RES_ID, mainPhotoResId);
                                        intent.putExtra(ChatDetailActivity.EXTRA_INITIAL_MSG_COUNT, 0);
                                        startActivity(intent);

                                        Intent resultIntent = new Intent();
                                        resultIntent.putExtra(EXTRA_NAME, currentUserName);
                                        resultIntent.putExtra(EXTRA_ACTION, ACTION_LIKE);
                                        setResult(RESULT_OK, resultIntent);
                                        finish();
                                    }

                                    @Override
                                    public void onContinue() {
                                        Intent resultIntent = new Intent();
                                        resultIntent.putExtra(EXTRA_NAME, currentUserName);
                                        resultIntent.putExtra(EXTRA_ACTION, ACTION_LIKE);
                                        setResult(RESULT_OK, resultIntent);
                                        finish();
                                    }
                                }
                        );
                        matchDialog.show();
                    })
                    .start();
        } else {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_NAME, currentUserName);
            resultIntent.putExtra(EXTRA_ACTION, ACTION_LIKE);
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    private void showBlockUserDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.lg_bottom_sheet_block_user, null);
        bottomSheetDialog.setContentView(view);

        View btnConfirm = view.findViewById(R.id.btnConfirmBlock);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                if (remoteProfile == null) { showBlockSuccessDialog(); return; }
                // POST /users/:id/block - chặn thật trên máy chủ rồi mới báo thành công
                new com.datezone.app.api.ApiClient(this).request(this, "POST", "users/" + remoteProfile.id + "/block", null,
                        new com.datezone.app.api.ApiClient.Callback() {
                            @Override public void onSuccess(org.json.JSONObject response) { showBlockSuccessDialog(); }
                            @Override public void onError(String message) {
                                Toast.makeText(ProfileObject2Activity.this, message, Toast.LENGTH_LONG).show();
                            }
                        });
            });
        }

        View btnCancel = view.findViewById(R.id.btnCancelBlock);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());
        }

        bottomSheetDialog.show();
    }

    private void showBlockSuccessDialog() {
        BlockSuccessDialog dialog = new BlockSuccessDialog(
                this,
                () -> {
                    Toast.makeText(this, "Đã chặn " + currentUserName + " thành công!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(ProfileObject2Activity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
        );
        dialog.show();
    }

    private void showReportUserDialog() {
        Intent intent = new Intent(ProfileObject2Activity.this, ReportUserActivity.class);
        intent.putExtra(ReportUserActivity.EXTRA_NAME, currentUserName);
        if (remoteProfile != null) intent.putExtra(ReportUserActivity.EXTRA_USER_ID, remoteProfile.id);
        startActivity(intent);
    }
}
