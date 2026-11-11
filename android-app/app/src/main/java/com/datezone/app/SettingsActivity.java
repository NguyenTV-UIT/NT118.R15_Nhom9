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
        loadSettings();
        loadSessions();
    }

    private boolean bindingSwitches;
    private long otherSessionId;

    private com.datezone.app.api.ApiClient api() {
        return new com.datezone.app.api.ApiClient(this);
    }

    /** GET /users/me/settings - email (che), lần đổi mật khẩu, 3 công tắc. */
    private void loadSettings() {
        api().request(this, "GET", "users/me/settings", null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                applySettings(response.getJSONObject("data"));
            }
            @Override public void onError(String message) {
                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applySettings(org.json.JSONObject data) {
        android.widget.TextView tvEmail = findViewById(R.id.tvSettingsEmailValue);
        String masked = com.datezone.app.api.ChatUi.text(data, "emailMasked");
        if (tvEmail != null && !masked.isEmpty()) tvEmail.setText(masked);
        android.widget.TextView tvPassword = findViewById(R.id.tvSettingsPasswordChanged);
        java.util.Date changed = com.datezone.app.api.ChatUi.parseTime(data.optString("passwordChangedAt"));
        if (tvPassword != null && changed != null) tvPassword.setText("Đổi lần cuối " + ago(changed));
        bindingSwitches = true;
        setSwitch(R.id.switchPushNotification, data.optBoolean("pushEnabled", true));
        setSwitch(R.id.switchOnlineStatus, data.optBoolean("showActiveStatus", true));
        setSwitch(R.id.switchShowDistance, data.optBoolean("showDistance", true));
        bindingSwitches = false;
    }

    private void setSwitch(int id, boolean checked) {
        android.widget.CompoundButton button = findViewById(id);
        if (button != null) button.setChecked(checked);
    }

    /** PUT /users/me/settings - đổi 1 công tắc; lỗi thì trả công tắc về trạng thái trên máy chủ. */
    private void bindSwitch(int id, String key) {
        android.widget.CompoundButton button = findViewById(id);
        if (button == null) return;
        button.setOnCheckedChangeListener((view, checked) -> {
            if (bindingSwitches) return;
            api().request(this, "PUT", "users/me/settings", com.datezone.app.api.ApiUi.data(key, checked),
                    new com.datezone.app.api.ApiClient.Callback() {
                        @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                            applySettings(response.getJSONObject("data"));
                        }
                        @Override public void onError(String message) {
                            Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                            loadSettings();
                        }
                    });
        });
    }

    private static String ago(java.util.Date date) {
        long minutes = Math.max(0, (System.currentTimeMillis() - date.getTime()) / 60000);
        if (minutes < 1) return "vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        if (minutes < 24 * 60) return (minutes / 60) + " giờ trước";
        long days = minutes / (24 * 60);
        if (days < 30) return days + " ngày trước";
        if (days < 365) return (days / 30) + " tháng trước";
        return (days / 365) + " năm trước";
    }

    /** GET /users/me/sessions - thiết bị này + 1 thiết bị khác gần nhất (nếu có). */
    private void loadSessions() {
        api().request(this, "GET", "users/me/sessions", null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                org.json.JSONArray sessions = response.getJSONArray("data");
                org.json.JSONObject current = null, other = null;
                int others = 0;
                for (int i = 0; i < sessions.length(); i++) {
                    org.json.JSONObject session = sessions.getJSONObject(i);
                    if (session.optBoolean("current")) current = session;
                    else {
                        if (other == null) other = session;
                        others++;
                    }
                }
                if (current != null) {
                    setText(R.id.tvSettingsCurrentDeviceName, com.datezone.app.api.ChatUi.text(current, "deviceName"));
                    setText(R.id.tvSettingsCurrentDeviceInfo, sessionInfo(current, 0));
                }
                otherSessionId = other == null ? 0 : other.optLong("id");
                int visibility = other == null ? View.GONE : View.VISIBLE;
                View layoutDevice = findViewById(R.id.layoutDeviceChromeWindows);
                View dividerDevice = findViewById(R.id.dividerDeviceChromeWindows);
                View logoutAll = findViewById(R.id.btnSettingsLogoutAllOtherDevices);
                if (layoutDevice != null) layoutDevice.setVisibility(visibility);
                if (dividerDevice != null) dividerDevice.setVisibility(visibility);
                if (logoutAll != null) logoutAll.setVisibility(visibility);
                if (other != null) {
                    setText(R.id.tvSettingsOtherDeviceName, com.datezone.app.api.ChatUi.text(other, "deviceName"));
                    setText(R.id.tvSettingsOtherDeviceInfo, sessionInfo(other, others - 1));
                }
            }
            @Override public void onError(String message) { }
        });
    }

    private void setText(int id, String text) {
        android.widget.TextView view = findViewById(id);
        if (view != null) view.setText(text);
    }

    private static String sessionInfo(org.json.JSONObject session, int more) {
        String location = com.datezone.app.api.ChatUi.text(session, "location");
        java.util.Date active = com.datezone.app.api.ChatUi.parseTime(session.optString("lastActiveAt"));
        String when = session.optBoolean("active") || session.optBoolean("current") ? "Đang hoạt động"
                : active == null ? "" : ago(active);
        String info = location.isEmpty() ? when : when.isEmpty() ? location : location + " · " + when;
        return more > 0 ? info + " (+" + more + " thiết bị khác)" : info;
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

        // 3 công tắc lưu thẳng lên máy chủ
        bindSwitch(R.id.switchPushNotification, "pushEnabled");
        bindSwitch(R.id.switchOnlineStatus, "showActiveStatus");
        bindSwitch(R.id.switchShowDistance, "showDistance");

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
            if (otherSessionId <= 0) return;
            // DELETE /users/me/sessions/:id - đăng xuất thiết bị đó ngay lập tức
            api().request(this, "DELETE", "users/me/sessions/" + otherSessionId, null, new com.datezone.app.api.ApiClient.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) {
                    Toast.makeText(SettingsActivity.this, "Đã đăng xuất khỏi thiết bị", Toast.LENGTH_LONG).show();
                    loadSessions();
                }
                @Override public void onError(String message) {
                    Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                    loadSessions();
                }
            });
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
            // DELETE /users/me/sessions - đăng xuất mọi thiết bị khác, giữ lại máy này
            api().request(this, "DELETE", "users/me/sessions", null, new com.datezone.app.api.ApiClient.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) {
                    Toast.makeText(SettingsActivity.this, response.optString("message", "Đã đăng xuất các thiết bị khác"), Toast.LENGTH_LONG).show();
                    loadSessions();
                }
                @Override public void onError(String message) {
                    Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
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
