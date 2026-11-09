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

public class DrinkingActivity extends AppCompatActivity {

    private RadioGroup rgDrinking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg13_activity_drinking);

        View root = findViewById(R.id.drinkingRoot);
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

        rgDrinking = findViewById(R.id.rgDrinking);

        // Nút Quay lại góc trên bên trái -> quay về lg12_activity_smoking
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Bỏ qua bước này
        View btnSkip = findViewById(R.id.btnSkip);
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(DrinkingActivity.this, ExerciseActivity.class);
                startActivity(intent);
            });
        }

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void checkAndContinue() {
        int selectedId = rgDrinking.getCheckedRadioButtonId();

        if (selectedId == -1) {
            Toast.makeText(this, "Vui lòng chọn thói quen uống rượu bia của bạn", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRb = findViewById(selectedId);
        String selectedDrinking = selectedRb != null ? selectedRb.getText().toString() : "";

        // Chuyển sang lg14_activity_exercise
        android.content.Intent intent = new android.content.Intent(DrinkingActivity.this, ExerciseActivity.class);
        intent.putExtra("EXTRA_DRINKING", selectedDrinking);
        ApiUi.saveProfile(this, ApiUi.data("drinking", selectedDrinking), intent);
    }
}
