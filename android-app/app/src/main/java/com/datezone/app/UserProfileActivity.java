package com.datezone.app;

import android.content.Intent;
import com.datezone.app.api.ApiClient;
import com.datezone.app.api.SessionStore;
import com.datezone.app.api.ProfileForm;
import com.datezone.app.api.RemoteImages;
import com.datezone.app.api.RemoteProfile;
import org.json.JSONObject;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class UserProfileActivity extends AppCompatActivity {

    private ImageView ivProfileCover;
    private TextView tvProfileNameAge;
    private TextView tvProfileGender;
    private TextView tvProfileLocation;
    private TextView tvProfileBio;

    private TextView tvProfileGoal;
    private TextView tvProfileZodiac;
    private TextView tvProfileSmoking;
    private TextView tvProfileDrinking;

    private TextView tvProfileExercise;
    private TextView tvProfileEducation;

    private ChipGroup chipGroupProfileInterests;

    private String[] userPhotoUrls = new String[0];
    private JSONObject options = new JSONObject();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.profile_activity_user);

        View root = findViewById(R.id.userProfileRoot);
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

        initViews();
        loadSavedUserData();
        setupListeners();
        refreshProfile();
    }

    private void initViews() {
        ivProfileCover = findViewById(R.id.ivProfileCover);
        tvProfileNameAge = findViewById(R.id.tvProfileNameAge);
        tvProfileGender = findViewById(R.id.tvProfileGender);
        tvProfileLocation = findViewById(R.id.tvProfileLocation);
        tvProfileBio = findViewById(R.id.tvProfileBio);

        tvProfileGoal = findViewById(R.id.tvProfileGoal);
        tvProfileZodiac = findViewById(R.id.tvProfileZodiac);
        tvProfileSmoking = findViewById(R.id.tvProfileSmoking);
        tvProfileDrinking = findViewById(R.id.tvProfileDrinking);
        tvProfileExercise = findViewById(R.id.tvProfileExercise);
        tvProfileEducation = findViewById(R.id.tvProfileEducation);

        chipGroupProfileInterests = findViewById(R.id.chipGroupProfileInterests);
    }

    private void loadSavedUserData() {
        bindUser(new SessionStore(this).user());
    }

    private void refreshProfile() {
        ApiClient api = new ApiClient(this);
        api.request(this, "GET", "users/me", null, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                JSONObject user = response.getJSONObject("data");
                new SessionStore(UserProfileActivity.this).saveUser(user);
                bindUser(user);
            }
            @Override public void onError(String message) {
                android.widget.Toast.makeText(UserProfileActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
            }
        });
        api.publicRequest(this, "GET", "meta/options", null, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                options = response.getJSONObject("data");
                loadSavedUserData();
            }
            @Override public void onError(String message) {
                android.widget.Toast.makeText(UserProfileActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindUser(JSONObject user) {
        userPhotoUrls = new String[0];
        tvProfileNameAge.setText(ProfileForm.text(user, "displayName"));
        tvProfileGender.setText("");
        tvProfileLocation.setText(ProfileForm.text(user, "city"));
        tvProfileBio.setText(ProfileForm.text(user, "bio"));
        chipGroupProfileInterests.removeAllViews();
        RemoteImages.load(ivProfileCover, null);
        ImageView[] gallery = {findViewById(R.id.ivProfileGalleryPhoto1), findViewById(R.id.ivProfileGalleryPhoto2),
                findViewById(R.id.ivProfileGalleryPhoto3), findViewById(R.id.ivProfileGalleryPhoto4)};
        TextView[] textViews = {tvProfileGoal, tvProfileZodiac, tvProfileSmoking, tvProfileDrinking, tvProfileExercise, tvProfileEducation};
        String[] titles = {"Mục tiêu", "Cung hoàng đạo", "Hút thuốc", "Rượu bia", "Tập luyện", "Học vấn"};
        for (int i = 0; i < textViews.length; i++) {
            String code = ProfileForm.text(user, ProfileForm.OPTION_KEYS[i]);
            String label = ProfileForm.label(options, ProfileForm.OPTION_KEYS[i], code);
            textViews[i].setText(titles[i] + ": " + (code.isEmpty() ? "Chưa cập nhật" : label.isEmpty() ? "Chưa tải được lựa chọn" : label));
        }
        for (ImageView image : gallery) { RemoteImages.load(image, null); ((View) image.getParent()).setVisibility(View.GONE); }
        if (!user.has("id")) return;
        try {
            // Preview follows public-profile visibility, even when /users/me includes private fields.
            JSONObject publicData = new JSONObject(user.toString());
            if (!user.optBoolean("showGender", true)) publicData.put("gender", JSONObject.NULL);
            RemoteProfile profile = new RemoteProfile(publicData);
            userPhotoUrls = profile.photos;
            tvProfileNameAge.setText(profile.name + (profile.age.isEmpty() ? "" : ", " + profile.age));
            tvProfileGender.setText(profile.details);
            tvProfileGender.setVisibility(profile.details.isEmpty() ? View.GONE : View.VISIBLE);
            tvProfileBio.setText(profile.bio.isEmpty() ? "Chưa có giới thiệu" : profile.bio);
            RemoteImages.load(ivProfileCover, profile.photos.length == 0 ? null : profile.photos[0]);
            for (int i = 0; i < gallery.length && i < profile.photos.length; i++) {
                ((View) gallery[i].getParent()).setVisibility(View.VISIBLE);
                RemoteImages.load(gallery[i], profile.photos[i]);
            }
            for (String interest : profile.interests) {
                Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_profile_interest_chip, chipGroupProfileInterests, false);
                chip.setText(interest);
                chipGroupProfileInterests.addView(chip);
            }
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Không đọc được hồ sơ", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        // Quay về ProfileHomeActivity khi bấm Back
        findViewById(R.id.btnProfileBack).setOnClickListener(v -> finish());

        // Bấm xem tất cả ảnh / chọn ảnh album -> Mở PhotoViewerActivity
        View.OnClickListener photoViewerListener = v -> {
            Intent intent = new Intent(UserProfileActivity.this, PhotoViewerActivity.class);
            intent.putExtra(PhotoViewerActivity.EXTRA_PHOTO_URLS, userPhotoUrls);
            startActivity(intent);
        };

        View btnViewAllPhotos = findViewById(R.id.btnProfileViewAllPhotos);
        if (btnViewAllPhotos != null) btnViewAllPhotos.setOnClickListener(photoViewerListener);

        View galleryCard1 = findViewById(R.id.profileGalleryCard1);
        if (galleryCard1 != null) galleryCard1.setOnClickListener(photoViewerListener);

        View galleryCard2 = findViewById(R.id.profileGalleryCard2);
        if (galleryCard2 != null) galleryCard2.setOnClickListener(photoViewerListener);

        View galleryCard3 = findViewById(R.id.profileGalleryCard3);
        if (galleryCard3 != null) galleryCard3.setOnClickListener(photoViewerListener);

        View galleryCard4 = findViewById(R.id.profileGalleryCard4);
        if (galleryCard4 != null) galleryCard4.setOnClickListener(photoViewerListener);
    }
}
