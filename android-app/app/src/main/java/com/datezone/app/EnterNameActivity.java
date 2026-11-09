package com.datezone.app;

import com.datezone.app.api.ApiUi;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class EnterNameActivity extends AppCompatActivity {

    private EditText edtName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg1_activity_enter_name);

        View root = findViewById(R.id.enterNameRoot);
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

        edtName = findViewById(R.id.edtName);
        if (savedInstanceState == null) {
            org.json.JSONObject user = new com.datezone.app.api.SessionStore(this).user();
            if (!user.isNull("displayName")) edtName.setText(user.optString("displayName", ""));
        }

        // Nút Quay lại
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void checkAndContinue() {
        String name = edtName.getText().toString().trim();

        if (name.isEmpty()) {
            edtName.setError("Vui lòng nhập tên của bạn");
            edtName.requestFocus();
            return;
        }

        if (name.length() < 2) {
            edtName.setError("Tên cần có ít nhất 2 ký tự");
            edtName.requestFocus();
            return;
        }

        // Chuyển sang màn hình lg2_add_photo
        Intent intent = new Intent(EnterNameActivity.this, AddPhotosActivity.class);
        intent.putExtra("EXTRA_USER_NAME", name);
        ApiUi.saveProfile(this, ApiUi.data("displayName", name), intent);
    }
}
