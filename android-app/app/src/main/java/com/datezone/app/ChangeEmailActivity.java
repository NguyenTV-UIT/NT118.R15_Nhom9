package com.datezone.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ChangeEmailActivity extends AppCompatActivity {

    /** Mã do máy chủ cấp sau khi xác thực OTP ở email hiện tại (hạn 10 phút). */
    public static final String EXTRA_EMAIL_CHANGE_TOKEN = "EXTRA_EMAIL_CHANGE_TOKEN";

    private EditText edtNewEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.profile_activity_change_email);

        View root = findViewById(R.id.changeEmailRoot);
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

        edtNewEmail = findViewById(R.id.edtNewEmail);

        setupListeners();
    }

    private void setupListeners() {
        // Nút Back
        findViewById(R.id.btnChangeEmailBack).setOnClickListener(v -> finish());

        // Link Quay lại Cài đặt
        View btnReturn = findViewById(R.id.btnReturnToSettings);
        if (btnReturn != null) {
            btnReturn.setOnClickListener(v -> finish());
        }

        // Nút Gửi mã OTP
        View btnSend = findViewById(R.id.btnSendOtp);
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendOtpCode());
        }
    }

    private void sendOtpCode() {
        String newEmail = edtNewEmail != null ? edtNewEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(newEmail) || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            Toast.makeText(this, "Vui lòng nhập địa chỉ email mới hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = getIntent() != null ? getIntent().getStringExtra(EXTRA_EMAIL_CHANGE_TOKEN) : null;
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, "Vui lòng xác thực email hiện tại trước khi đổi email", Toast.LENGTH_LONG).show();
            return;
        }
        // PUT /users/me/email - đổi sang email mới (lần đăng nhập sau dùng email này)
        com.datezone.app.api.ApiUi.send(this, R.id.btnSendOtp, true, "PUT", "users/me/email",
                com.datezone.app.api.ApiUi.data("emailChangeToken", token, "newEmail", newEmail), response -> {
                    org.json.JSONObject data = response.getJSONObject("data");
                    if (data.optBoolean("otpRequired")) {
                        Toast.makeText(this, "Máy chủ yêu cầu xác thực email mới bằng OTP, app chưa hỗ trợ bước này.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String saved = data.optString("email", newEmail);
                    com.datezone.app.api.SessionStore store = new com.datezone.app.api.SessionStore(this);
                    org.json.JSONObject user = store.user();
                    user.put("email", saved);
                    store.saveUser(user);
                    Intent intent = new Intent(ChangeEmailActivity.this, ChangeEmailSuccessActivity.class);
                    intent.putExtra(OtpVerificationActivity.EXTRA_EMAIL, saved);
                    startActivity(intent);
                    finish();
                });
    }
}
