package com.datezone.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText edtEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg_activity_forgot_password);

        View root = findViewById(R.id.forgotPasswordRoot);
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

        edtEmail = findViewById(R.id.edtEmail);

        // Nút top-left Quay lại -> back về trang trước
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());

        // Text "Quay lại đăng nhập" -> về trang đăng nhập
        findViewById(R.id.tvBackToLogin).setOnClickListener(view -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Nút Gửi mã OTP
        findViewById(R.id.btnSendOtp).setOnClickListener(view -> checkInputAndSendOtp());
    }

    private void checkInputAndSendOtp() {
        String email = edtEmail.getText().toString().trim();

        if (email.isEmpty()) {
            edtEmail.setError("Vui lòng nhập địa chỉ email");
            edtEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Địa chỉ email không hợp lệ");
            edtEmail.requestFocus();
            return;
        }

        com.datezone.app.api.ApiUi.send(this, R.id.btnSendOtp, false, "POST", "auth/password/forgot",
                com.datezone.app.api.ApiUi.data("email", email), response -> {
                    Intent intent = new Intent(this, OtpVerificationActivity.class);
                    intent.putExtra(OtpVerificationActivity.EXTRA_FLOW_TYPE, OtpVerificationActivity.FLOW_FORGOT_PASSWORD);
                    intent.putExtra(OtpVerificationActivity.EXTRA_EMAIL, email);
                    intent.putExtra("resendAfter", response.getJSONObject("data").optInt("resendAfter", 30));
                    startActivity(intent);
                });
    }
}
