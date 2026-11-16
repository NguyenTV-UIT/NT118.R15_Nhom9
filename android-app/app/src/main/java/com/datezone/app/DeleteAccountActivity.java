package com.datezone.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DeleteAccountActivity extends AppCompatActivity {

    private EditText edtConfirmDeleteText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.profile_activity_delete_account);

        View root = findViewById(R.id.deleteAccountRoot);
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

        edtConfirmDeleteText = findViewById(R.id.edtConfirmDeleteText);

        setupListeners();
    }

    private void setupListeners() {
        // Nút Quay lại
        findViewById(R.id.btnDeleteAccountBack).setOnClickListener(v -> finish());

        // Nút Giữ lại tài khoản -> Hủy xóa và quay về
        View btnKeepAccount = findViewById(R.id.btnKeepAccount);
        if (btnKeepAccount != null) {
            btnKeepAccount.setOnClickListener(v -> finish());
        }

        // Nút Xóa tài khoản vĩnh viễn
        View btnConfirmDelete = findViewById(R.id.btnConfirmDeleteAccountPermanently);
        if (btnConfirmDelete != null) {
            btnConfirmDelete.setOnClickListener(v -> performDeleteAccount());
        }
    }

    private void performDeleteAccount() {
        String confirm = edtConfirmDeleteText != null ? edtConfirmDeleteText.getText().toString().trim() : "";
        android.widget.RadioGroup group = findViewById(R.id.radioGroupDeleteReason);
        int checked = group == null ? -1 : group.getCheckedRadioButtonId();
        String reason = checked == R.id.radioReason1 ? "found_match" : checked == R.id.radioReason2 ? "taking_break"
                : checked == R.id.radioReason3 ? "privacy" : checked == R.id.radioReason4 ? "other" : null;
        // DELETE /users/me - máy chủ kiểm tra chữ XÓA rồi xoá vĩnh viễn; thành công mới dọn dữ liệu trên máy
        com.datezone.app.api.ApiUi.send(this, R.id.btnConfirmDeleteAccountPermanently, true, "DELETE", "users/me",
                com.datezone.app.api.ApiUi.data("confirm", confirm, "reason", reason), response -> {
                    new com.datezone.app.api.SessionStore(this).clear();
                    finishDeleteAccount();
                });
    }

    private void finishDeleteAccount() {
        // Xóa sạch bộ nhớ dữ liệu hồ sơ
        SharedPreferences prefs = getSharedPreferences(ProfileEditActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        // Bắn thông báo đẩy về thiết bị
        sendPushNotification("Bảo mật tài khoản", "Tài khoản DateZone của bạn đã được xóa vĩnh viễn khỏi hệ thống.");

        Toast.makeText(this, "🔔 Đã xóa tài khoản vĩnh viễn thành công!", Toast.LENGTH_LONG).show();

        // Chuyển hướng về trang đăng nhập và xóa toàn bộ Activity stack
        Intent intent = new Intent(DeleteAccountActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void sendPushNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "datezone_account_delete";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Thông báo xóa tài khoản",
                    NotificationManager.IMPORTANCE_HIGH
            );
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_warning_red)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            if (notificationManager != null) {
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            }
        } catch (Exception ignored) {
        }
    }
}
