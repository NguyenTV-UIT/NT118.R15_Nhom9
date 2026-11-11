package com.datezone.app;

import com.datezone.app.api.ApiUi;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class BioActivity extends AppCompatActivity {

    private EditText edtBio;
    private TextView tvBioCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg16_activity_bio);

        View root = findViewById(R.id.bioRoot);
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

        edtBio = findViewById(R.id.edtBio);
        tvBioCount = findViewById(R.id.tvBioCount);

        // Đếm số ký tự tiểu sử thời gian thực
        edtBio.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tvBioCount.setText(s.length() + "/300 ký tự");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Nút Quay lại góc trên bên trái -> quay về lg15_activity_education
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Bỏ qua bước này -> Hoàn tất tạo hồ sơ
        View btnSkip = findViewById(R.id.btnSkip);
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> finishOnboarding());
        }

        // Nút Hoàn thành dùng chung
        findViewById(R.id.btnFinish).setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        String bio = edtBio.getText().toString().trim();

        // Chuyển sang màn hình thông báo hồ sơ sẵn sàng lgf_activity_profile_ready
        Intent intent = new Intent(BioActivity.this, ProfileReadyActivity.class);
        intent.putExtra("EXTRA_BIO", bio);
        ApiUi.saveProfile(this, ApiUi.data("bio", bio), intent);
    }
}
