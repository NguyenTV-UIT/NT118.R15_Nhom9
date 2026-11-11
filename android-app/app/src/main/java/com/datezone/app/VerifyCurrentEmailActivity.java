package com.datezone.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class VerifyCurrentEmailActivity extends AppCompatActivity {

    private EditText edtCurrentEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.profile_activity_verify_current_email);

        View root = findViewById(R.id.verifyCurrentEmailRoot);
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

        edtCurrentEmail = findViewById(R.id.edtCurrentEmailInput);

        SharedPreferences prefs = getSharedPreferences(ProfileEditActivity.PREFS_NAME, Context.MODE_PRIVATE);
        // Email thật của tài khoản đang đăng nhập (không còn dùng email mẫu)
        org.json.JSONObject sessionUser = new com.datezone.app.api.SessionStore(this).user();
        String savedEmail = sessionUser.isNull("email") ? "" : sessionUser.optString("email", "");
        if (edtCurrentEmail != null && !TextUtils.isEmpty(savedEmail)) {
            edtCurrentEmail.setText(savedEmail);
        }

        setupListeners();
    }

    private void setupListeners() {
        findViewById(R.id.btnVerifyCurrentEmailBack).setOnClickListener(v -> finish());

        View btnReturn = findViewById(R.id.btnReturnSettings);
        if (btnReturn != null) {
            btnReturn.setOnClickListener(v -> finish());
        }

        View btnSend = findViewById(R.id.btnSendOtpToCurrentEmail);
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendOtpToCurrentEmail());
        }
    }

    private void sendOtpToCurrentEmail() {
        String emailStr = edtCurrentEmail != null ? edtCurrentEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(emailStr) || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()) {
            Toast.makeText(this, "Vui lòng nhập email hiện tại hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // POST /users/me/email/verify-current - máy chủ kiểm tra đúng email hiện tại rồi mới gửi OTP
        com.datezone.app.api.ApiUi.send(this, R.id.btnSendOtpToCurrentEmail, true, "POST", "users/me/email/verify-current",
                com.datezone.app.api.ApiUi.data("email", emailStr), response -> {
                    Toast.makeText(this, "Đã gửi mã OTP tới " + emailStr, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(VerifyCurrentEmailActivity.this, OtpVerificationActivity.class);
                    intent.putExtra(OtpVerificationActivity.EXTRA_FLOW_TYPE, OtpVerificationActivity.FLOW_VERIFY_CURRENT_EMAIL);
                    intent.putExtra(OtpVerificationActivity.EXTRA_EMAIL, emailStr);
                    intent.putExtra("resendAfter", response.getJSONObject("data").optInt("resendAfter", 30));
                    startActivity(intent);
                });
    }
}
