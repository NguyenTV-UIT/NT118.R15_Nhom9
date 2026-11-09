package com.datezone.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ProfileHomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.profile_activity_home);

        View root = findViewById(R.id.myProfileRoot);
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

        setupButtons();
        setupBottomNavigation();
        loadSavedProfileData();
    }

    private void refreshProfile() {
        new com.datezone.app.api.ApiClient(this).request(this, "GET", "users/me", null,
                new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                        new com.datezone.app.api.SessionStore(ProfileHomeActivity.this).saveUser(response.getJSONObject("data"));
                        loadSavedProfileData();
                    }
                    @Override public void onError(String message) {
                        android.widget.Toast.makeText(ProfileHomeActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedProfileData();
        refreshProfile();
    }

    private void loadSavedProfileData() {
        org.json.JSONObject user = new com.datezone.app.api.SessionStore(this).user();
        String name = user.isNull("displayName") ? "Chưa có tên" : user.optString("displayName", "Chưa có tên");
        String bio = user.isNull("bio") ? "Chưa có giới thiệu" : user.optString("bio");
        String gender = "male".equals(user.optString("gender")) ? "Nam" : "female".equals(user.optString("gender")) ? "Nữ" : "Phi nhị nguyên";
        String height = user.isNull("heightCm") ? "—" : user.optInt("heightCm") + " cm";
        String weight = user.isNull("weightKg") ? "—" : user.optInt("weightKg") + " kg";
        String location = user.isNull("city") ? "Chưa chọn khu vực" : user.optString("city");

        TextView tvName = findViewById(R.id.tvMyProfileName);
        if (tvName != null) tvName.setText(name);

        TextView tvAgeGender = findViewById(R.id.tvMyProfileAgeGender);
        if (tvAgeGender != null) tvAgeGender.setText((user.isNull("age") ? "" : user.optInt("age") + " tuổi")
                + (user.isNull("gender") ? "" : " • " + gender));

        TextView tvMeasurements = findViewById(R.id.tvMyProfileMeasurements);
        if (tvMeasurements != null) tvMeasurements.setText("Cao " + height + " • Nặng " + weight);

        TextView tvBio = findViewById(R.id.tvMyProfileBio);
        if (tvBio != null) tvBio.setText(bio);

        TextView tvLocation = findViewById(R.id.tvMyProfileLocation);
        if (tvLocation != null) tvLocation.setText("📍 " + location);
        com.datezone.app.api.RemoteImages.load(findViewById(R.id.ivMyProfileAvatar),
                user.isNull("avatarUrl") ? null : user.optString("avatarUrl"));
    }

    private void setupButtons() {
        View btnEdit = findViewById(R.id.btnMyProfileEdit);
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileHomeActivity.this, ProfileEditActivity.class);
                startActivity(intent);
            });
        }

        View btnPreview = findViewById(R.id.btnMyProfilePreview);
        if (btnPreview != null) {
            btnPreview.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileHomeActivity.this, UserProfileActivity.class);
                startActivity(intent);
            });
        }

        View rowSettings = findViewById(R.id.rowMyProfileSettings);
        if (rowSettings != null) {
            rowSettings.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileHomeActivity.this, SettingsActivity.class);
                startActivity(intent);
            });
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.myProfileBottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navDiscoveryProfile);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.navDiscoveryExplore) {
                    Intent intent = new Intent(ProfileHomeActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryLikes) {
                    Intent intent = new Intent(ProfileHomeActivity.this, MatchesActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryChat) {
                    Intent intent = new Intent(ProfileHomeActivity.this, ChatListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryProfile) {
                    return true;
                }
                return false;
            });
        }
    }
}
