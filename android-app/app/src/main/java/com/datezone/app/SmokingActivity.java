package com.datezone.app;

import com.datezone.app.api.ApiUi;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SmokingActivity extends AppCompatActivity {

    private RadioGroup rgSmoking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg12_activity_smoking);

        View root = findViewById(R.id.smokingRoot);
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

        rgSmoking = findViewById(R.id.rgSmoking);

        // Nút Quay lại góc trên bên trái -> quay về lg11_activity_zodiac
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Bỏ qua bước này
        View btnSkip = findViewById(R.id.btnSkip);
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(SmokingActivity.this, DrinkingActivity.class);
                startActivity(intent);
            });
        }

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void checkAndContinue() {
        int selectedId = rgSmoking.getCheckedRadioButtonId();

        if (selectedId == -1) {
            Toast.makeText(this, "Vui lòng chọn thói quen hút thuốc của bạn", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRb = findViewById(selectedId);
        String selectedSmoking = selectedRb != null ? selectedRb.getText().toString() : "";

        // Chuyển sang lg13_activity_drinking
        android.content.Intent intent = new android.content.Intent(SmokingActivity.this, DrinkingActivity.class);
        intent.putExtra("EXTRA_SMOKING", selectedSmoking);
        ApiUi.saveProfile(this, ApiUi.data("smoking", selectedSmoking), intent);
    }
}
