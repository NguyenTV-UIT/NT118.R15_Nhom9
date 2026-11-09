package com.datezone.app;

import com.datezone.app.api.ApiUi;

import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class WeightActivity extends AppCompatActivity {

    private TextView tvWeight;
    private SeekBar seekWeight;

    private static final int MIN_WEIGHT_KG = 35;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg8_activity_weight);

        View root = findViewById(R.id.weightRoot);
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

        tvWeight = findViewById(R.id.tvWeight);
        seekWeight = findViewById(R.id.seekWeight);

        // Cập nhật cân nặng thời gian thực khi kéo thanh trượt (35..120 kg)
        seekWeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int weightKg = MIN_WEIGHT_KG + progress;
                tvWeight.setText(String.valueOf(weightKg));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Nút Quay lại góc trên bên trái -> quay về lg7_activity_height
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Bỏ qua bước này
        View btnSkip = findViewById(R.id.btnSkip);
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(WeightActivity.this, InterestsActivity.class);
                startActivity(intent);
            });
        }

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void checkAndContinue() {
        int weightKg = MIN_WEIGHT_KG + seekWeight.getProgress();

        // Chuyển sang lg9_activity_interests
        android.content.Intent intent = new android.content.Intent(WeightActivity.this, InterestsActivity.class);
        intent.putExtra("EXTRA_WEIGHT_KG", weightKg);
        ApiUi.saveProfile(this, ApiUi.data("weightKg", weightKg), intent);
    }
}
