package com.datezone.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class OtpVerificationActivity extends AppCompatActivity {

    public static final String EXTRA_FLOW_TYPE = "EXTRA_FLOW_TYPE";
    public static final String EXTRA_EMAIL = "EXTRA_EMAIL";
    public static final String EXTRA_PHONE_NUMBER = "EXTRA_EMAIL";

    public static final String FLOW_LOGIN = "FLOW_LOGIN";
    public static final String FLOW_REGISTER = "FLOW_REGISTER";
    public static final String FLOW_FORGOT_PASSWORD = "FLOW_FORGOT_PASSWORD";
    public static final String FLOW_CHANGE_EMAIL = "FLOW_CHANGE_EMAIL";
    public static final String FLOW_VERIFY_CURRENT_EMAIL = "FLOW_VERIFY_CURRENT_EMAIL";
    public static final String FLOW_CHANGE_PASSWORD = "FLOW_CHANGE_PASSWORD";

    private EditText[] otpBoxes;
    private TextView tvOtpSubtitle;
    private TextView tvResendOtp;

    private String flowType = FLOW_FORGOT_PASSWORD;
    private String email = "";
    private CountDownTimer countDownTimer;
    private boolean isResendClickable = false;
    private int resendAfter = 30;
    private long resendAvailableAt;
    private String resetToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg_activity_otp_verification);

        View root = findViewById(R.id.otpRoot);
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

        if (getIntent() != null) {
            if (getIntent().hasExtra(EXTRA_FLOW_TYPE)) {
                flowType = getIntent().getStringExtra(EXTRA_FLOW_TYPE);
            }
            if (getIntent().hasExtra(EXTRA_EMAIL)) {
                email = getIntent().getStringExtra(EXTRA_EMAIL);
            } else if (getIntent().hasExtra(EXTRA_PHONE_NUMBER)) {
                email = getIntent().getStringExtra(EXTRA_PHONE_NUMBER);
            }
        }

        tvOtpSubtitle = findViewById(R.id.tvOtpSubtitle);
        tvResendOtp = findViewById(R.id.tvResendOtp);

        setupSubtitle();

        // Khởi tạo 6 ô OTP
        otpBoxes = new EditText[]{
                findViewById(R.id.edtOtp1),
                findViewById(R.id.edtOtp2),
                findViewById(R.id.edtOtp3),
                findViewById(R.id.edtOtp4),
                findViewById(R.id.edtOtp5),
                findViewById(R.id.edtOtp6)
        };

        setupOtpInputs();

        // Nút Back quay về trang trước
        findViewById(R.id.btnBack).setOnClickListener(view -> finish());

        // Nút Xác nhận
        findViewById(R.id.btnVerify).setOnClickListener(view -> verifyOtpCode());

        // Text Gửi lại mã
        tvResendOtp.setOnClickListener(view -> {
            if (isResendClickable) {
                resendOtpCode();
            }
        });

        resendAfter = getIntent().getIntExtra("resendAfter", 30);
        if (savedInstanceState != null) resendAvailableAt = savedInstanceState.getLong("resendAvailableAt");
        startResendTimer();
    }

    private void setupSubtitle() {
        String maskedEmail = maskEmail(email);
        String subtitleText = "Nhập mã gồm 6 chữ số chúng tôi đã gửi tới\n" + maskedEmail;
        tvOtpSubtitle.setText(subtitleText);
    }

    private String maskEmail(String emailStr) {
        if (emailStr == null || emailStr.trim().isEmpty()) {
            return "email của bạn";
        }
        int atIndex = emailStr.indexOf('@');
        if (atIndex > 2) {
            String name = emailStr.substring(0, atIndex);
            String domain = emailStr.substring(atIndex);
            return name.substring(0, 2) + "•••" + domain;
        }
        return emailStr;
    }

    private void setupOtpInputs() {
        for (int i = 0; i < otpBoxes.length; i++) {
            final int index = i;

            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && index < otpBoxes.length - 1) {
                        otpBoxes[index + 1].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            otpBoxes[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (otpBoxes[index].getText().toString().isEmpty() && index > 0) {
                        otpBoxes[index - 1].requestFocus();
                        otpBoxes[index - 1].setText("");
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private void verifyOtpCode() {
        StringBuilder sb = new StringBuilder();
        for (EditText box : otpBoxes) {
            sb.append(box.getText().toString().trim());
        }

        String otp = sb.toString();
        if (!otp.matches("\\d{6}")) {
            Toast.makeText(this, "Vui lòng nhập đủ 6 chữ số OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        if (FLOW_VERIFY_CURRENT_EMAIL.equals(flowType)) {
            // Đổi email bước 2: POST /users/me/email/verify-otp -> emailChangeToken cho màn nhập email mới
            com.datezone.app.api.ApiUi.send(this, R.id.btnVerify, true, "POST", "users/me/email/verify-otp",
                    com.datezone.app.api.ApiUi.data("otp", otp), response -> {
                        Intent intent = new Intent(OtpVerificationActivity.this, ChangeEmailActivity.class);
                        intent.putExtra(ChangeEmailActivity.EXTRA_EMAIL_CHANGE_TOKEN,
                                response.getJSONObject("data").getString("emailChangeToken"));
                        startActivity(intent);
                        finish();
                    });
            return;
        }
        if (!FLOW_FORGOT_PASSWORD.equals(flowType)) {
            Toast.makeText(this, "Chức năng xác minh này chưa được máy chủ hỗ trợ.", Toast.LENGTH_LONG).show();
            return;
        }
        com.datezone.app.api.ApiUi.send(this, R.id.btnVerify, false, "POST", "auth/password/verify-otp",
                com.datezone.app.api.ApiUi.data("email", email, "otp", otp), response -> {
                    resetToken = response.getJSONObject("data").getString("resetToken");
                    onOtpVerificationSuccess();
                });
    }

    private void onOtpVerificationSuccess() {
        if (FLOW_FORGOT_PASSWORD.equals(flowType) && resetToken != null) {
            // Sau khi bấm xác nhận từ Quên mật khẩu -> Mới hiện ra trang Đặt lại mật khẩu
            Intent intent = new Intent(OtpVerificationActivity.this, ResetPasswordActivity.class);
            intent.putExtra(EXTRA_EMAIL, email);
            intent.putExtra(ResetPasswordActivity.EXTRA_RESET_TOKEN, resetToken);
            startActivity(intent);
            finish();
        }
    }

    private void startResendTimer() {
        isResendClickable = false;
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        int textSecondaryColor = ContextCompat.getColor(this, R.color.text_secondary);
        int brandPrimaryColor = ContextCompat.getColor(this, R.color.brand_primary);

        tvResendOtp.setTextColor(textSecondaryColor);
        tvResendOtp.setTypeface(null, android.graphics.Typeface.NORMAL);

        if (resendAvailableAt == 0) resendAvailableAt = System.currentTimeMillis() + resendAfter * 1000L;
        long remaining = Math.max(0, resendAvailableAt - System.currentTimeMillis());
        countDownTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvResendOtp.setText("Gửi lại mã sau " + seconds + "s");
            }

            @Override
            public void onFinish() {
                isResendClickable = true;
                tvResendOtp.setText("Gửi lại");
                tvResendOtp.setTextColor(brandPrimaryColor);
                tvResendOtp.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }.start();
    }

    private void resendOtpCode() {
        if (FLOW_VERIFY_CURRENT_EMAIL.equals(flowType)) {
            com.datezone.app.api.ApiUi.send(this, R.id.tvResendOtp, true, "POST", "users/me/email/verify-current",
                    com.datezone.app.api.ApiUi.data("email", email), response -> {
                        resendAfter = response.getJSONObject("data").optInt("resendAfter", 30);
                        resendAvailableAt = 0;
                        for (EditText box : otpBoxes) box.setText("");
                        otpBoxes[0].requestFocus();
                        startResendTimer();
                    });
            return;
        }
        if (!FLOW_FORGOT_PASSWORD.equals(flowType)) return;
        com.datezone.app.api.ApiUi.send(this, R.id.tvResendOtp, false, "POST", "auth/password/forgot",
                com.datezone.app.api.ApiUi.data("email", email), response -> {
                    resendAfter = response.getJSONObject("data").optInt("resendAfter", 30);
                    resendAvailableAt = 0;
                    for (EditText box : otpBoxes) box.setText("");
                    otpBoxes[0].requestFocus();
                    startResendTimer();
                });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("resendAvailableAt", resendAvailableAt);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
