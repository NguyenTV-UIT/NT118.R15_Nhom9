package com.datezone.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText edtCurrentPassword;
    private ImageView btnToggleVisibility;
    private TextView tvSubtitle;

    private boolean isPasswordVisible = false;
    private String userEmail = "minhanh.new@gmail.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.profile_activity_change_password);

        View root = findViewById(R.id.changePasswordRoot);
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

        edtCurrentPassword = findViewById(R.id.edtCurrentPasswordInput);
        btnToggleVisibility = findViewById(R.id.btnTogglePasswordVisibility);
        tvSubtitle = findViewById(R.id.tvChangePasswordSubtitle);

        SharedPreferences prefs = getSharedPreferences(ProfileEditActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String savedEmail = prefs.getString("KEY_SAVED_EMAIL", "minhanh.new@gmail.com");
        if (!TextUtils.isEmpty(savedEmail)) {
            userEmail = savedEmail;
        }

        if (tvSubtitle != null) {
            tvSubtitle.setText("Nhập mật khẩu hiện tại và mật khẩu mới để bảo vệ tài khoản.");
        }

        setupListeners();
    }

    private void setupListeners() {
        // Nút Back
        findViewById(R.id.btnChangePasswordBack).setOnClickListener(v -> finish());

        // An / Hiện mật khẩu
        if (btnToggleVisibility != null && edtCurrentPassword != null) {
            btnToggleVisibility.setOnClickListener(v -> {
                isPasswordVisible = !isPasswordVisible;
                if (isPasswordVisible) {
                    edtCurrentPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                } else {
                    edtCurrentPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
                edtCurrentPassword.setSelection(edtCurrentPassword.getText().length());
            });
        }

        // Link Quên mật khẩu hiện tại? Đặt lại mật khẩu
        View btnForgot = findViewById(R.id.btnForgotPasswordLink);
        if (btnForgot != null) {
            btnForgot.setOnClickListener(v -> {
                Intent intent = new Intent(ChangePasswordActivity.this, ForgotPasswordActivity.class);
                startActivity(intent);
            });
        }

        // Nút Gửi mã OTP
        View btnSendOtp = findViewById(R.id.btnSendOtpChangePassword);
        if (btnSendOtp != null) {
            btnSendOtp.setOnClickListener(v -> validateAndSendOtp());
        }
    }

    private void validateAndSendOtp() {
        String password = edtCurrentPassword != null ? edtCurrentPassword.getText().toString() : "";

        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Vui lòng nhập mật khẩu hiện tại của bạn", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText newPassword = findViewById(R.id.edtChangeNewPassword);
        EditText confirm = findViewById(R.id.edtChangeConfirmPassword);
        String replacement = newPassword.getText().toString();
        if (replacement.length() < 6 || replacement.length() > 72) {
            newPassword.setError("Mật khẩu cần từ 6 đến 72 ký tự");
            return;
        }
        if (!replacement.equals(confirm.getText().toString())) {
            confirm.setError("Mật khẩu nhập lại không khớp");
            return;
        }
        com.datezone.app.api.ApiUi.send(this, R.id.btnSendOtpChangePassword, true, "PUT", "auth/password",
                com.datezone.app.api.ApiUi.data("currentPassword", password, "newPassword", replacement), response -> {
                    new com.datezone.app.api.SessionStore(this).clear();
                    Toast.makeText(this, "Đã đổi mật khẩu. Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }
}
