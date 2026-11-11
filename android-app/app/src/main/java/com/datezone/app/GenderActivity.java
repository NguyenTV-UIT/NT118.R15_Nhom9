package com.datezone.app;

import com.datezone.app.api.ApiUi;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class GenderActivity extends AppCompatActivity {

    private RadioGroup rgGender;
    private SwitchCompat switchShowGender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg4_activity_gender);

        View root = findViewById(R.id.genderRoot);
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

        rgGender = findViewById(R.id.rgGender);
        switchShowGender = findViewById(R.id.switchShowGender);

        // Nút Quay lại dùng chung
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void checkAndContinue() {
        int selectedId = rgGender.getCheckedRadioButtonId();

        if (selectedId == -1) {
            Toast.makeText(this, "Vui lòng chọn giới tính của bạn", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRb = findViewById(selectedId);
        String selectedGender = selectedRb != null ? selectedRb.getText().toString() : "";
        boolean isShowOnProfile = switchShowGender.isChecked();

        // Chuyển sang trang lg5_activity_dating_preference
        android.content.Intent intent = new android.content.Intent(GenderActivity.this, DatingPreferenceActivity.class);
        intent.putExtra("EXTRA_GENDER", selectedGender);
        intent.putExtra("EXTRA_SHOW_GENDER", isShowOnProfile);
        ApiUi.saveProfile(this, ApiUi.data("gender", selectedGender, "showGender", isShowOnProfile), intent);
    }
}
