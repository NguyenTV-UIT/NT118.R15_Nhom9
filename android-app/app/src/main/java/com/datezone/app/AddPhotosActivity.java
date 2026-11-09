package com.datezone.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AddPhotosActivity extends AppCompatActivity {

    private final View[] photoSlots = new View[6];
    private final long[] photoIds = new long[6];
    private boolean processing;
    private int currentSelectingIndex = -1;

    private ActivityResultLauncher<String> photoPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg2_activity_add_photos);

        View root = findViewById(R.id.addPhotosRoot);
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

        // Đăng ký Launcher chọn ảnh từ thư viện
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && currentSelectingIndex >= 0 && currentSelectingIndex < 6) {
                        uploadPhotoToSlot(currentSelectingIndex, uri);
                    }
                }
        );

        // Ánh xạ 6 ô ảnh
        photoSlots[0] = findViewById(R.id.photoSlot1);
        photoSlots[1] = findViewById(R.id.photoSlot2);
        photoSlots[2] = findViewById(R.id.photoSlot3);
        photoSlots[3] = findViewById(R.id.photoSlot4);
        photoSlots[4] = findViewById(R.id.photoSlot5);
        photoSlots[5] = findViewById(R.id.photoSlot6);

        // Hiển thị ghi chú "(Ảnh chính)" trên ô ảnh đầu tiên
        if (photoSlots[0] != null) {
            View tvTag = photoSlots[0].findViewById(R.id.tvMainPhotoTag);
            if (tvTag != null) {
                tvTag.setVisibility(View.VISIBLE);
            }
        }

        for (int i = 0; i < photoSlots.length; i++) {
            final int index = i;
            if (photoSlots[i] != null) {
                // Click vào slot -> Mở thư viện ảnh
                photoSlots[i].setOnClickListener(v -> {
                    if (processing) return;
                    if (photoIds[index] != 0) {
                        Toast.makeText(this, "Nhấn nút xóa trên ảnh trước khi chọn ảnh thay thế.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentSelectingIndex = index;
                    photoPickerLauncher.launch("image/*");
                });

                // Click vào nút xóa ảnh
                View btnRemove = photoSlots[i].findViewById(R.id.btnRemovePhoto);
                if (btnRemove != null) {
                    btnRemove.setOnClickListener(v -> removePhotoFromSlot(index));
                }
            }
        }

        // Nút Quay lại
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
        loadRemotePhotos();
    }

    private void showRemotePhoto(int index, org.json.JSONObject photo) throws Exception {
        photoIds[index] = photo.getLong("id");
        View slot = photoSlots[index];
        if (slot == null) return;

        ImageView ivPhoto = slot.findViewById(R.id.ivPhoto);
        View layoutPlaceholder = slot.findViewById(R.id.layoutPlaceholder);
        View btnRemovePhoto = slot.findViewById(R.id.btnRemovePhoto);

        if (ivPhoto != null) {
            com.datezone.app.api.RemoteImages.load(ivPhoto, photo.getString("url"));
            ivPhoto.setVisibility(View.VISIBLE);
        }
        if (layoutPlaceholder != null) {
            layoutPlaceholder.setVisibility(View.GONE);
        }
        if (btnRemovePhoto != null) {
            btnRemovePhoto.setVisibility(View.VISIBLE);
        }
    }

    private void clearPhotoSlot(int index) {
        photoIds[index] = 0;
        View slot = photoSlots[index];
        if (slot == null) return;

        ImageView ivPhoto = slot.findViewById(R.id.ivPhoto);
        View layoutPlaceholder = slot.findViewById(R.id.layoutPlaceholder);
        View btnRemovePhoto = slot.findViewById(R.id.btnRemovePhoto);

        if (ivPhoto != null) {
            ivPhoto.setImageDrawable(null);
            ivPhoto.setVisibility(View.GONE);
        }
        if (layoutPlaceholder != null) {
            layoutPlaceholder.setVisibility(View.VISIBLE);
        }
        if (btnRemovePhoto != null) {
            btnRemovePhoto.setVisibility(View.GONE);
        }
    }

    private void setProcessing(boolean value) {
        processing = value;
        findViewById(R.id.btnContinue).setEnabled(!value);
        ((android.widget.TextView) findViewById(R.id.btnContinue)).setText(value ? "Đang xử lý…" : "Tiếp tục");
    }

    private void loadRemotePhotos() {
        setProcessing(true);
        new com.datezone.app.api.ApiClient(this).request(this, "GET", "users/me", null,
                new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                        org.json.JSONObject user = response.getJSONObject("data");
                        new com.datezone.app.api.SessionStore(AddPhotosActivity.this).saveUser(user);
                        org.json.JSONArray photos = user.getJSONArray("photos");
                        for (int i = 0; i < photos.length() && i < 6; i++) showRemotePhoto(i, photos.getJSONObject(i));
                        setProcessing(false);
                    }
                    @Override public void onError(String message) {
                        // Do not allow uploads until we know how many photos already exist.
                        Toast.makeText(AddPhotosActivity.this, message, Toast.LENGTH_LONG).show();
                        findViewById(R.id.btnContinue).setEnabled(true);
                        ((android.widget.TextView) findViewById(R.id.btnContinue)).setText("Thử tải lại");
                        findViewById(R.id.btnContinue).setOnClickListener(v -> {
                            findViewById(R.id.btnContinue).setOnClickListener(next -> checkAndContinue());
                            loadRemotePhotos();
                        });
                    }
                });
    }

    private void uploadPhotoToSlot(int index, Uri uri) {
        if (processing || photoIds[index] != 0) return;
        setProcessing(true);
        new com.datezone.app.api.ApiClient(this).uploadPhoto(this, uri,
                new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                        showRemotePhoto(index, response.getJSONObject("data"));
                        setProcessing(false);
                    }
                    @Override public void onError(String message) {
                        setProcessing(false);
                        Toast.makeText(AddPhotosActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void removePhotoFromSlot(int index) {
        if (processing || photoIds[index] == 0) return;
        setProcessing(true);
        new com.datezone.app.api.ApiClient(this).request(this, "DELETE", "users/me/photos/" + photoIds[index], null,
                new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) {
                        clearPhotoSlot(index);
                        setProcessing(false);
                    }
                    @Override public void onError(String message) {
                        setProcessing(false);
                        Toast.makeText(AddPhotosActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkAndContinue() {
        int uploadedCount = 0;
        if (processing) return;
        for (long id : photoIds) {
            if (id != 0) {
                uploadedCount++;
            }
        }

        if (uploadedCount < 2) {
            Toast.makeText(this, "Vui lòng tải lên ít nhất 2 ảnh để tiếp tục", Toast.LENGTH_SHORT).show();
            return;
        }

        // Chuyển sang lg3_activity_birthday
        Intent intent = new Intent(AddPhotosActivity.this, BirthdayActivity.class);
        startActivity(intent);
    }
}
