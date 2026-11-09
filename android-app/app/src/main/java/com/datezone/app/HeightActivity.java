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

public class HeightActivity extends AppCompatActivity {

    private TextView tvHeight;
    private SeekBar seekHeight;

    private static final int MIN_HEIGHT_CM = 140;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg7_activity_height);

        View root = findViewById(R.id.heightRoot);
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

        tvHeight = findViewById(R.id.tvHeight);
        seekHeight = findViewById(R.id.seekHeight);

        // Cập nhật giá trị chiều cao thời gian thực khi kéo SeekBar (140..200 cm)
        seekHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int heightCm = MIN_HEIGHT_CM + progress;
                tvHeight.setText(String.valueOf(heightCm));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Nút Quay lại góc trên bên trái -> quay về lg6_location_distance
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void checkAndContinue() {
        int heightCm = MIN_HEIGHT_CM + seekHeight.getProgress();

        // Chuyển sang lg8_activity_weight
        android.content.Intent intent = new android.content.Intent(HeightActivity.this, WeightActivity.class);
        intent.putExtra("EXTRA_HEIGHT_CM", heightCm);
        ApiUi.saveProfile(this, ApiUi.data("heightCm", heightCm), intent);
    }
}
