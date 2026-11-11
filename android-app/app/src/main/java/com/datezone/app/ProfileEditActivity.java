package com.datezone.app;

import android.content.Intent;
import com.datezone.app.api.ApiClient;
import com.datezone.app.api.ApiUi;
import com.datezone.app.api.SessionStore;
import com.datezone.app.api.RemoteImages;
import com.datezone.app.api.ProfileForm;
import org.json.JSONObject;
import org.json.JSONArray;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class ProfileEditActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "UserProfilePrefs";
    public static final String KEY_NAME = "KEY_NAME";
    public static final String KEY_BIO = "KEY_BIO";
    public static final String KEY_GENDER = "KEY_GENDER";
    public static final String KEY_HEIGHT = "KEY_HEIGHT";
    public static final String KEY_WEIGHT = "KEY_WEIGHT";
    public static final String KEY_LOCATION = "KEY_LOCATION";
    public static final String KEY_RELATIONSHIP = "KEY_RELATIONSHIP";
    public static final String KEY_ZODIAC = "KEY_ZODIAC";
    public static final String KEY_SMOKING = "KEY_SMOKING";
    public static final String KEY_DRINKING = "KEY_DRINKING";
    public static final String KEY_EXERCISE = "KEY_EXERCISE";
    public static final String KEY_EDUCATION = "KEY_EDUCATION";

    private GridLayout gridPhotos;
    private TextView tvPhotoCount;
    private final List<JSONObject> photosList = new ArrayList<>();

    private ChipGroup chipGroupSelectedInterests;
    private final ArrayList<String> selectedInterestsList = new ArrayList<>();

    private EditText edtName;
    private EditText edtBio;
    private EditText edtGender;
    private EditText edtHeight;
    private EditText edtWeight;
    private EditText edtLocation;

    private TextView tvRelationship;
    private TextView tvZodiac;
    private TextView tvSmoking;
    private TextView tvDrinking;
    private TextView tvExercise;
    private TextView tvEducation;

    private ActivityResultLauncher<String> photoPickerLauncher;
    private ActivityResultLauncher<Intent> interestsLauncher;
    private JSONObject original = new JSONObject();
    private JSONObject options = new JSONObject();
    private JSONObject optionCodes = new JSONObject();
    private boolean loaded, busy;
    private Bundle pendingDraft;

    private EditText[] fieldViews() { return new EditText[]{edtName, edtBio, edtGender, edtHeight, edtWeight, edtLocation}; }
    private String[] fieldKeys() { return new String[]{"displayName", "bio", "gender", "heightCm", "weightKg", "city"}; }
    private TextView[] optionViews() { return new TextView[]{tvRelationship, tvZodiac, tvSmoking, tvDrinking, tvExercise, tvEducation}; }
    private int[] optionGroups() { return new int[]{R.id.chipGroupEditProfileRelationship, R.id.chipGroupEditProfileZodiac,
            R.id.chipGroupEditProfileSmoking, R.id.chipGroupEditProfileDrinking, R.id.chipGroupEditProfileExercise, R.id.chipGroupEditProfileEducation}; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.profile_activity_edit);

        View root = findViewById(R.id.editProfileRoot);
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

                return insets.inset(bars.left, bars.top, bars.right, bars.bottom);
            });
            ViewCompat.requestApplyInsets(root);
        }

        // Khởi tạo launcher chọn ảnh từ thiết bị
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && photosList.size() < 6) {
                        uploadPhoto(uri);
                    }
                }
        );

        // Khởi tạo launcher mở trang Sở thích
        interestsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> updatedInterests = result.getData().getStringArrayListExtra(InterestsActivity.EXTRA_SELECTED_INTERESTS);
                        if (updatedInterests != null) {
                            selectedInterestsList.clear();
                            selectedInterestsList.addAll(updatedInterests);
                            renderSelectedInterests();
                        }
                    }
                }
        );

        initViews();
        pendingDraft = savedInstanceState;
        setupListeners();
        edtGender.setKeyListener(null);
        edtGender.setFocusable(false);
        edtGender.setOnClickListener(v -> chooseGender());
        loadProfile();
    }

    private void initViews() {
        gridPhotos = findViewById(R.id.gridEditProfilePhotos);
        tvPhotoCount = findViewById(R.id.tvEditProfilePhotoCount);

        chipGroupSelectedInterests = findViewById(R.id.chipGroupSelectedInterests);

        edtName = findViewById(R.id.edtEditProfileName);
        edtBio = findViewById(R.id.edtEditProfileBio);
        edtGender = findViewById(R.id.edtEditProfileGender);
        edtHeight = findViewById(R.id.edtEditProfileHeight);
        edtWeight = findViewById(R.id.edtEditProfileWeight);
        edtLocation = findViewById(R.id.edtEditProfileLocation);

        tvRelationship = findViewById(R.id.tvEditProfileRelationship);
        tvZodiac = findViewById(R.id.tvEditProfileZodiac);
        tvSmoking = findViewById(R.id.tvEditProfileSmoking);
        tvDrinking = findViewById(R.id.tvEditProfileDrinking);
        tvExercise = findViewById(R.id.tvEditProfileExercise);
        tvEducation = findViewById(R.id.tvEditProfileEducation);
    }

    private void renderPhotosGrid() {
        if (gridPhotos == null) return;
        gridPhotos.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int slotWidthPx = (int) (100 * density);
        int slotHeightPx = (int) (132 * density);
        int marginPx = (int) (6 * density);
        int brandPrimaryColor = ContextCompat.getColor(this, R.color.brand_primary);

        if (tvPhotoCount != null) {
            tvPhotoCount.setText(photosList.size() + "/6 ảnh");
        }

        for (int i = 0; i < 6; i++) {
            final int index = i;

            CardView cardView = new CardView(this);
            GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
            cardParams.width = slotWidthPx;
            cardParams.height = slotHeightPx;
            cardParams.setMargins(marginPx, marginPx, marginPx, marginPx);
            cardView.setLayoutParams(cardParams);
            cardView.setRadius(14 * density);
            cardView.setCardElevation(0);
            cardView.setPreventCornerOverlap(false);

            FrameLayout frameLayout = new FrameLayout(this);
            frameLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            if (i < photosList.size()) {
                // Ô có ảnh
                ImageView ivPhoto = new ImageView(this);
                ivPhoto.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
                ivPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);

                RemoteImages.load(ivPhoto, photosList.get(i).optString("url"));

                // Nút 'X' xóa ảnh ở góc trên bên phải
                ImageView btnRemove = new ImageView(this);
                int btnSizePx = (int) (24 * density);
                FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(btnSizePx, btnSizePx);
                removeParams.gravity = Gravity.TOP | Gravity.END;
                removeParams.setMargins(0, (int) (4 * density), (int) (4 * density), 0);
                btnRemove.setLayoutParams(removeParams);
                btnRemove.setBackgroundResource(R.drawable.bg_circle_add);
                btnRemove.getBackground().mutate().setTint(Color.parseColor("#80000000"));
                btnRemove.setPadding((int) (5 * density), (int) (5 * density), (int) (5 * density), (int) (5 * density));
                btnRemove.setImageResource(R.drawable.ic_remove);
                btnRemove.setColorFilter(Color.WHITE);

                btnRemove.setOnClickListener(v -> {
                    deletePhoto(index);
                });

                frameLayout.addView(ivPhoto);
                frameLayout.addView(btnRemove);

                // Thẻ 'Ảnh chính' ở góc dưới bên trái của ảnh đầu tiên
                if (i == 0) {
                    TextView tvMainBadge = new TextView(this);
                    FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                    );
                    badgeParams.gravity = Gravity.BOTTOM | Gravity.START;
                    badgeParams.setMargins((int) (6 * density), 0, 0, (int) (6 * density));
                    tvMainBadge.setLayoutParams(badgeParams);
                    tvMainBadge.setBackgroundResource(R.drawable.bg_circle_add);
                    tvMainBadge.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
                    tvMainBadge.setText("Ảnh chính");
                    tvMainBadge.setTextColor(Color.WHITE);
                    tvMainBadge.setTextSize(10f);
                    tvMainBadge.setTypeface(null, android.graphics.Typeface.BOLD);

                    frameLayout.addView(tvMainBadge);
                }
            } else {
                // Ô trống '+' viền nét đứt
                cardView.setCardBackgroundColor(Color.parseColor("#F8FAFC"));
                cardView.setBackgroundResource(R.drawable.bg_photo_slot);

                ImageView ivPlus = new ImageView(this);
                int plusSizePx = (int) (26 * density);
                FrameLayout.LayoutParams plusParams = new FrameLayout.LayoutParams(plusSizePx, plusSizePx);
                plusParams.gravity = Gravity.CENTER;
                ivPlus.setLayoutParams(plusParams);
                ivPlus.setImageResource(R.drawable.ic_add);
                ivPlus.setColorFilter(brandPrimaryColor);

                frameLayout.addView(ivPlus);

                if (i == photosList.size()) {
                    // Ô '+' kế tiếp cho phép bấm chọn thêm ảnh mới từ thiết bị
                    cardView.setOnClickListener(v -> { if (!busy && loaded) photoPickerLauncher.launch("image/*"); });
                } else {
                    cardView.setOnClickListener(v -> Toast.makeText(this, "Vui lòng thêm ảnh theo thứ tự", Toast.LENGTH_SHORT).show());
                }
            }

            cardView.addView(frameLayout);
            gridPhotos.addView(cardView);
        }
    }

    private void renderSelectedInterests() {
        if (chipGroupSelectedInterests == null) return;
        chipGroupSelectedInterests.removeAllViews();

        int brandPrimaryColor = ContextCompat.getColor(this, R.color.brand_primary);

        for (String interest : selectedInterestsList) {
            Chip chip = new Chip(this);
            chip.setText(interest);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setTextColor(Color.WHITE);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(brandPrimaryColor));
            chipGroupSelectedInterests.addView(chip);
        }
    }

    private void setOptionText(TextView textView, String value) {
        if (textView != null) {
            textView.setText(value);
            textView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void setupListeners() {
        findViewById(R.id.btnEditProfileBack).setOnClickListener(v -> finish());

        View btnSaveHeader = findViewById(R.id.btnEditProfileSave);
        if (btnSaveHeader != null) btnSaveHeader.setOnClickListener(v -> saveProfileData());

        View btnSaveBottom = findViewById(R.id.btnEditProfileSaveChanges);
        if (btnSaveBottom != null) btnSaveBottom.setOnClickListener(v -> saveProfileData());

        // Nút Chỉnh sửa Sở thích
        View btnEditInterests = findViewById(R.id.btnEditProfileInterests);
        if (btnEditInterests != null) {
            btnEditInterests.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileEditActivity.this, InterestsActivity.class);
                intent.putStringArrayListExtra(InterestsActivity.EXTRA_SELECTED_INTERESTS, selectedInterestsList);
                intent.putExtra(InterestsActivity.EXTRA_IS_EDIT, true);
                if (loaded && !busy) interestsLauncher.launch(intent);
            });
        }

        // Mở rộng / thu gọn các mục Mối quan hệ & Lối sống chuẩn như FiltersActivity
        setupExpandableLifestyleSections();
    }

    private void setupExpandableLifestyleSections() {
        setupExpandableRow(
                findViewById(R.id.rowEditProfileRelationship),
                findViewById(R.id.containerEditProfileRelationshipExpanded),
                findViewById(R.id.ivArrowEditProfileRelationship),
                findViewById(R.id.chipGroupEditProfileRelationship),
                tvRelationship
        );

        setupExpandableRow(
                findViewById(R.id.rowEditProfileZodiac),
                findViewById(R.id.containerEditProfileZodiacExpanded),
                findViewById(R.id.ivArrowEditProfileZodiac),
                findViewById(R.id.chipGroupEditProfileZodiac),
                tvZodiac
        );

        setupExpandableRow(
                findViewById(R.id.rowEditProfileSmoking),
                findViewById(R.id.containerEditProfileSmokingExpanded),
                findViewById(R.id.ivArrowEditProfileSmoking),
                findViewById(R.id.chipGroupEditProfileSmoking),
                tvSmoking
        );

        setupExpandableRow(
                findViewById(R.id.rowEditProfileDrinking),
                findViewById(R.id.containerEditProfileDrinkingExpanded),
                findViewById(R.id.ivArrowEditProfileDrinking),
                findViewById(R.id.chipGroupEditProfileDrinking),
                tvDrinking
        );

        setupExpandableRow(
                findViewById(R.id.rowEditProfileExercise),
                findViewById(R.id.containerEditProfileExerciseExpanded),
                findViewById(R.id.ivArrowEditProfileExercise),
                findViewById(R.id.chipGroupEditProfileExercise),
                tvExercise
        );

        setupExpandableRow(
                findViewById(R.id.rowEditProfileEducation),
                findViewById(R.id.containerEditProfileEducationExpanded),
                findViewById(R.id.ivArrowEditProfileEducation),
                findViewById(R.id.chipGroupEditProfileEducation),
                tvEducation
        );
    }

    private void setupExpandableRow(View rowView, LinearLayout container, ImageView arrow, ChipGroup chipGroup, TextView targetTextView) {
        if (rowView != null && container != null) {
            rowView.setOnClickListener(v -> toggleSection(container, arrow));
        }

    }

    private void toggleSection(LinearLayout container, ImageView arrow) {
        if (container == null) return;

        if (container.getVisibility() == View.GONE) {
            container.setVisibility(View.VISIBLE);
            if (arrow != null) arrow.animate().rotation(180f).setDuration(200).start();
        } else {
            container.setVisibility(View.GONE);
            if (arrow != null) arrow.animate().rotation(0f).setDuration(200).start();
        }
    }

    private void setFormEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setFormEnabled(group.getChildAt(i), enabled);
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        setFormEnabled(findViewById(R.id.editProfileScroll), loaded && !value);
        findViewById(R.id.btnEditProfileSave).setEnabled(!value);
        findViewById(R.id.btnEditProfileSaveChanges).setEnabled(!value);
        ((TextView) findViewById(R.id.btnEditProfileSave)).setText(value ? "…" : loaded ? "Lưu" : "Tải lại");
        ((TextView) findViewById(R.id.btnEditProfileSaveChanges)).setText(value ? "Đang xử lý…" : loaded ? "Lưu thay đổi" : "Thử tải lại hồ sơ");
    }

    private void showError(String message) {
        setBusy(false);
        TextView status = findViewById(R.id.tvEditProfileStatus);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void loadProfile() {
        if (busy) return;
        loaded = false;
        for (EditText field : fieldViews()) field.setText("");
        setBusy(true);
        ((TextView) findViewById(R.id.tvEditProfileStatus)).setText("Đang tải hồ sơ…");
        ApiClient api = new ApiClient(this);
        api.request(this, "GET", "users/me", null, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                original = response.getJSONObject("data");
                new SessionStore(ProfileEditActivity.this).saveUser(original);
                api.publicRequest(ProfileEditActivity.this, "GET", "meta/options", null, new ApiClient.Callback() {
                    @Override public void onSuccess(JSONObject response) throws Exception {
                        options = response.getJSONObject("data");
                        bindProfile();
                        loaded = true;
                        setBusy(false);
                        findViewById(R.id.tvEditProfileStatus).setVisibility(View.GONE);
                    }
                    @Override public void onError(String message) { showError(message); }
                });
            }
            @Override public void onError(String message) { showError(message); }
        });
    }

    private void bindProfile() throws Exception {
        EditText[] fields = fieldViews();
        String[] keys = fieldKeys();
        for (int i = 0; i < keys.length; i++) {
            String value = ProfileForm.text(original, keys[i]);
            if ("gender".equals(keys[i])) value = ProfileForm.label(options, "gender", value);
            fields[i].setText(value);
        }
        optionCodes = new JSONObject();
        for (String key : ProfileForm.OPTION_KEYS) optionCodes.put(key, original.opt(key) == null ? JSONObject.NULL : original.opt(key));
        selectedInterestsList.clear();
        JSONArray interests = original.optJSONArray("interests");
        if (interests != null) for (int i = 0; i < interests.length(); i++)
            selectedInterestsList.add(interests.getJSONObject(i).getString("name"));
        if (pendingDraft != null && pendingDraft.containsKey("profileDraft")) {
            JSONObject draft = new JSONObject(pendingDraft.getString("profileDraft"));
            for (int i = 0; i < keys.length; i++) fields[i].setText(draft.optString(keys[i]));
            optionCodes = new JSONObject(pendingDraft.getString("optionDraft", "{}"));
            ArrayList<String> selected = pendingDraft.getStringArrayList("interestDraft");
            if (selected != null) { selectedInterestsList.clear(); selectedInterestsList.addAll(selected); }
        }
        pendingDraft = null;
        photosList.clear();
        JSONArray photos = original.optJSONArray("photos");
        if (photos != null) for (int i = 0; i < photos.length(); i++) photosList.add(photos.getJSONObject(i));
        bindOptionGroups();
        renderPhotosGrid();
        renderSelectedInterests();
    }

    private void bindOptionGroups() throws Exception {
        for (int i = 0; i < ProfileForm.OPTION_KEYS.length; i++) {
            String key = ProfileForm.OPTION_KEYS[i];
            TextView summary = optionViews()[i];
            ChipGroup group = findViewById(optionGroups()[i]);
            group.removeAllViews();
            group.setSingleSelection(true);
            group.setSelectionRequired(true);
            JSONArray choices = options.optJSONArray(key);
            String selected = ProfileForm.text(optionCodes, key);
            setOptionText(summary, selected.isEmpty() ? "Chưa chọn" : ProfileForm.label(options, key, selected));
            for (int j = -1; choices != null && j < choices.length(); j++) {
                String code = j < 0 ? "" : choices.getJSONObject(j).getString("code");
                String label = j < 0 ? "Chưa chọn" : choices.getJSONObject(j).optString("shortLabel", choices.getJSONObject(j).getString("label"));
                Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_profile_interest_chip, group, false);
                chip.setId(View.generateViewId());
                chip.setCheckable(true);
                chip.setClickable(true);
                chip.setFocusable(true);
                chip.setText(label);
                chip.setChecked(code.equals(selected));
                chip.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) {
                        try { optionCodes.put(key, code.isEmpty() ? JSONObject.NULL : code); }
                        catch (org.json.JSONException e) { throw new IllegalStateException(e); }
                        setOptionText(summary, label);
                    }
                });
                group.addView(chip);
            }
        }
    }

    private void chooseGender() {
        if (!loaded || busy) return;
        JSONArray choices = options.optJSONArray("gender");
        if (choices == null) return;
        String[] labels = new String[choices.length()];
        for (int i = 0; i < labels.length; i++) labels[i] = choices.optJSONObject(i).optString("label");
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Giới tính")
                .setItems(labels, (dialog, position) -> edtGender.setText(labels[position])).show();
    }

    private java.util.Map<String, String> formFields() {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        EditText[] fields = fieldViews();
        String[] keys = fieldKeys();
        for (int i = 0; i < keys.length; i++) result.put(keys[i], fields[i].getText().toString());
        return result;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (loaded) {
            state.putString("profileDraft", new JSONObject(formFields()).toString());
            state.putString("optionDraft", optionCodes.toString());
            state.putStringArrayList("interestDraft", new ArrayList<>(selectedInterestsList));
        }
        super.onSaveInstanceState(state);
    }

    private void saveProfileData() {
        if (busy) return;
        if (!loaded) { loadProfile(); return; }
        final JSONObject patch;
        try {
            patch = ProfileForm.patch(original, options, formFields(), optionCodes);
            if (selectedInterestsList.size() < 3 || selectedInterestsList.size() > 20)
                throw new IllegalArgumentException("Vui lòng chọn từ 3 đến 20 sở thích.");
        } catch (Exception e) { showError(e.getMessage()); return; }
        setBusy(true);
        if (patch.length() == 0) { saveInterests(false); return; }
        new ApiClient(this).request(this, "PUT", "users/me/profile", patch, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                original = response.getJSONObject("data");
                new SessionStore(ProfileEditActivity.this).saveUser(original);
                saveInterests(true);
            }
            @Override public void onError(String message) { showError(message); }
        });
    }

    private void saveInterests(boolean profileSaved) {
        java.util.Set<String> before = new java.util.HashSet<>();
        JSONArray old = original.optJSONArray("interests");
        if (old != null) for (int i = 0; i < old.length(); i++) before.add(old.optJSONObject(i).optString("name"));
        if (before.equals(new java.util.HashSet<>(selectedInterestsList))) { saved(); return; }
        new ApiClient(this).request(this, "PUT", "users/me/interests",
                ApiUi.data("interestNames", new JSONArray(selectedInterestsList)), new ApiClient.Callback() {
                    @Override public void onSuccess(JSONObject response) throws Exception {
                        original = response.getJSONObject("data");
                        new SessionStore(ProfileEditActivity.this).saveUser(original);
                        saved();
                    }
                    @Override public void onError(String message) {
                        showError((profileSaved ? "Thông tin cơ bản đã lưu, nhưng chưa lưu được sở thích. " : "") + message);
                    }
                });
    }

    private void saved() {
        Toast.makeText(this, "Đã cập nhật hồ sơ", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void cachePhotos() throws Exception {
        JSONArray photos = new JSONArray(photosList);
        JSONObject user = new SessionStore(this).user();
        user.put("photos", photos).put("avatarUrl", photosList.isEmpty() ? JSONObject.NULL : photosList.get(0).getString("url"));
        new SessionStore(this).saveUser(user);
        original.put("photos", photos);
        renderPhotosGrid();
    }

    private void uploadPhoto(Uri uri) {
        if (busy || !loaded) return;
        setBusy(true);
        new ApiClient(this).uploadPhoto(this, uri, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                photosList.add(response.getJSONObject("data"));
                cachePhotos();
                setBusy(false);
            }
            @Override public void onError(String message) { showError(message); }
        });
    }

    private void deletePhoto(int index) {
        if (busy || !loaded || index >= photosList.size()) return;
        JSONObject onboarding = original.optJSONObject("onboarding");
        if (onboarding != null && onboarding.optBoolean("completed") && photosList.size() <= 2) {
            showError("Hồ sơ cần ít nhất 2 ảnh. Hãy thêm ảnh mới trước khi xóa ảnh này.");
            return;
        }
        setBusy(true);
        new ApiClient(this).request(this, "DELETE", "users/me/photos/" + photosList.get(index).optLong("id"), null, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                photosList.remove(index);
                cachePhotos();
                setBusy(false);
            }
            @Override public void onError(String message) { showError(message); }
        });
    }
}
