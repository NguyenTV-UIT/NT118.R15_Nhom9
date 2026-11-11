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

public class RegisterActivity extends AppCompatActivity {

    private EditText edtEmail;
    private EditText edtPassword;
    private EditText edtConfirmPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lg_activity_register);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        View root = findViewById(R.id.registerRoot);
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
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);

        findViewById(R.id.btnRegister).setOnClickListener(view -> checkRegisterInput());

        findViewById(R.id.tvLogin).setOnClickListener(view -> finish());
    }

    private void checkRegisterInput() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString();
        String confirmPassword = edtConfirmPassword.getText().toString();

        if (email.isEmpty()) {
            showError(edtEmail, "Vui lòng nhập địa chỉ email");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(edtEmail, "Địa chỉ email không hợp lệ");
            return;
        }

        if (password.length() < 8 || password.length() > 72) {
            showError(edtPassword, "Mật khẩu cần từ 8 đến 72 ký tự");
            return;
        }

        if (confirmPassword.isEmpty()) {
            showError(edtConfirmPassword, "Vui lòng nhập lại mật khẩu");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError(edtConfirmPassword, "Mật khẩu nhập lại không khớp");
            return;
        }

        com.datezone.app.api.ApiUi.send(this, R.id.btnRegister, false, "POST", "auth/register",
                com.datezone.app.api.ApiUi.data("email", email, "password", password),
                response -> com.datezone.app.api.AuthFlow.signedIn(this, response));
    }

    private void showError(EditText input, String message) {
        input.setError(message);
        input.requestFocus();
    }
}
