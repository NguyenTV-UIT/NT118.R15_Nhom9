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

public class DatingPreferenceActivity extends AppCompatActivity {

    private RadioGroup rgDatingPreference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg5_activity_dating_preference);

        View root = findViewById(R.id.datingPreferenceRoot);
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

        rgDatingPreference = findViewById(R.id.rgDatingPreference);

        // Nút Quay lại góc trên bên trái -> quay về lg4_gender
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void checkAndContinue() {
        int selectedId = rgDatingPreference.getCheckedRadioButtonId();

        if (selectedId == -1) {
            Toast.makeText(this, "Vui lòng chọn đối tượng bạn muốn hẹn hò", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRb = findViewById(selectedId);
        String selectedPreference = selectedRb != null ? selectedRb.getText().toString() : "";

        // Chuyển sang lg6_activity_location_distance
        android.content.Intent intent = new android.content.Intent(DatingPreferenceActivity.this, LocationDistanceActivity.class);
        intent.putExtra("EXTRA_DATING_PREFERENCE", selectedPreference);
        ApiUi.save(this, "users/me/preferences", ApiUi.data("interestedIn", selectedPreference), intent);
    }
}
