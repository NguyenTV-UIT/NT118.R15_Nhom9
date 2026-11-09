package com.datezone.app;

import com.datezone.app.api.ApiUi;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class ZodiacActivity extends AppCompatActivity {

    private ChipGroup chipGroupZodiac;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg11_activity_zodiac);

        View root = findViewById(R.id.zodiacRoot);
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

        chipGroupZodiac = findViewById(R.id.chipGroupZodiac);
        chipGroupZodiac.clearCheck();

        // Nút Quay lại góc trên bên trái -> quay về lg10_activity_relationship_goal
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Bỏ qua bước này
        View btnSkip = findViewById(R.id.btnSkip);
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(ZodiacActivity.this, SmokingActivity.class);
                startActivity(intent);
            });
        }

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void checkAndContinue() {
        int checkedChipId = chipGroupZodiac.getCheckedChipId();

        if (checkedChipId == View.NO_ID) {
            Toast.makeText(this, "Vui lòng chọn cung hoàng đạo của bạn", Toast.LENGTH_SHORT).show();
            return;
        }

        Chip selectedChip = findViewById(checkedChipId);
        String selectedZodiac = selectedChip != null ? selectedChip.getText().toString() : "";

        // Chuyển sang lg12_activity_smoking
        android.content.Intent intent = new android.content.Intent(ZodiacActivity.this, SmokingActivity.class);
        intent.putExtra("EXTRA_ZODIAC", selectedZodiac);
        ApiUi.saveProfile(this, ApiUi.data("zodiac", selectedZodiac), intent);
    }
}
