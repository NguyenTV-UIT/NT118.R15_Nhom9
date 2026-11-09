package com.datezone.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.profile_activity_settings);

        View root = findViewById(R.id.settingsRoot);
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

        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSavedEmailDisplay();
    }

    private void updateSavedEmailDisplay() {
        org.json.JSONObject user = new com.datezone.app.api.SessionStore(this).user();
        String savedEmail = user.isNull("email") ? "" : user.optString("email", "");
        android.widget.TextView tvEmail = findViewById(R.id.tvSettingsEmailValue);
        if (tvEmail != null && savedEmail != null) {
            tvEmail.setText(savedEmail);
        }
    }

    private void setupListeners() {
        // Nút Back
        findViewById(R.id.btnSettingsBack).setOnClickListener(v -> finish());

        // Sửa Email -> Mở màn hình VerifyCurrentEmailActivity
        View rowEditEmail = findViewById(R.id.rowSettingsEditEmail);
        if (rowEditEmail != null) {
            rowEditEmail.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, VerifyCurrentEmailActivity.class);
                startActivity(intent);
            });
        }

        // Đổi Mật khẩu -> Mở màn hình ChangePasswordActivity
        View rowEditPassword = findViewById(R.id.rowSettingsEditPassword);
        if (rowEditPassword != null) {
            rowEditPassword.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, ChangePasswordActivity.class);
                startActivity(intent);
            });
        }

        // Đăng xuất thiết bị phụ
        View btnLogoutDevice = findViewById(R.id.btnSettingsLogoutDevice);
        if (btnLogoutDevice != null) {
            btnLogoutDevice.setOnClickListener(v -> showLogoutDeviceDialog());
        }

        // Đăng xuất khỏi tất cả thiết bị khác
        View btnLogoutAllOther = findViewById(R.id.btnSettingsLogoutAllOtherDevices);
        if (btnLogoutAllOther != null) {
            btnLogoutAllOther.setOnClickListener(v -> showLogoutAllDevicesDialog());
        }

        // Nút Đăng xuất chính
        View cardLogout = findViewById(R.id.cardSettingsLogout);
        if (cardLogout != null) {
            cardLogout.setOnClickListener(v -> showLogoutDialog());
        }

        // Nút Xóa tài khoản -> Mở màn hình DeleteAccountActivity
        View btnDeleteAccount = findViewById(R.id.btnSettingsDeleteAccount);
        if (btnDeleteAccount != null) {
            btnDeleteAccount.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, DeleteAccountActivity.class);
                startActivity(intent);
            });
        }
    }

    private void showLogoutDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnDialogConfirmLogout).setOnClickListener(v -> {
            dialog.dismiss();
            performLogout();
        });

        dialogView.findViewById(R.id.btnDialogCancelLogout).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showLogoutDeviceDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_logout_device, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnDialogConfirmLogoutDevice).setOnClickListener(v -> {
            dialog.dismiss();

            // Ẩn thiết bị Chrome trên Windows khỏi danh sách trong trang Cài đặt
            View layoutDevice = findViewById(R.id.layoutDeviceChromeWindows);
            View dividerDevice = findViewById(R.id.dividerDeviceChromeWindows);
            if (layoutDevice != null) layoutDevice.setVisibility(View.GONE);
            if (dividerDevice != null) dividerDevice.setVisibility(View.GONE);

            // Bắn thông báo đẩy về thiết bị thành công
            sendPushNotification("Bảo mật tài khoản", "Đã đăng xuất khỏi thiết bị Chrome trên Windows thành công!");
            Toast.makeText(this, "🔔 Đã đăng xuất khỏi thiết bị Chrome trên Windows thành công!", Toast.LENGTH_LONG).show();
        });

        dialogView.findViewById(R.id.btnDialogCancelLogoutDevice).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showLogoutAllDevicesDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_logout_all_devices, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnDialogConfirmLogoutAllDevices).setOnClickListener(v -> {
            dialog.dismiss();

            // Ẩn thiết bị phụ khỏi danh sách trong trang Cài đặt
            View layoutDevice = findViewById(R.id.layoutDeviceChromeWindows);
            View dividerDevice = findViewById(R.id.dividerDeviceChromeWindows);
            if (layoutDevice != null) layoutDevice.setVisibility(View.GONE);
            if (dividerDevice != null) dividerDevice.setVisibility(View.GONE);

            // Bắn thông báo đẩy về thiết bị thành công
            sendPushNotification("Bảo mật tài khoản", "Đã đăng xuất khỏi tất cả các thiết bị khác thành công!");
            Toast.makeText(this, "🔔 Đã đăng xuất khỏi tất cả các thiết bị khác thành công!", Toast.LENGTH_LONG).show();
        });

        dialogView.findViewById(R.id.btnDialogCancelLogoutAllDevices).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void sendPushNotification(String title, String message) {
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        String channelId = "datezone_device_logout";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId,
                    "Thông báo đăng xuất thiết bị",
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_settings_device)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            if (notificationManager != null) {
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            }
        } catch (Exception ignored) {
        }
    }

    private void performLogout() {
        com.datezone.app.api.SessionStore session = new com.datezone.app.api.SessionStore(this);
        String refresh = session.refreshToken();
        session.clear();
        if (!refresh.isEmpty()) new com.datezone.app.api.ApiClient(this).publicRequest(this, "POST", "auth/logout",
                com.datezone.app.api.ApiUi.data("refreshToken", refresh), new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) { }
                    @Override public void onError(String message) { }
                });
        Toast.makeText(this, "Đã đăng xuất thành công!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
