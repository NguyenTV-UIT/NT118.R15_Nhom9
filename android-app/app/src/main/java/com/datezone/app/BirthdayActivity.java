package com.datezone.app;

import com.datezone.app.api.ApiUi;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Calendar;

public class BirthdayActivity extends AppCompatActivity {

    private EditText edtDay;
    private EditText edtMonth;
    private EditText edtYear;
    private TextView tvAgeResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg3_activity_birthday);

        View root = findViewById(R.id.birthdayRoot);
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

        edtDay = findViewById(R.id.edtDay);
        edtMonth = findViewById(R.id.edtMonth);
        edtYear = findViewById(R.id.edtYear);
        tvAgeResult = findViewById(R.id.tvAgeResult);

        setupDateInputs();

        // Nút Quay lại
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void setupDateInputs() {
        TextWatcher dateWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculateAndDisplayAge();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        edtDay.addTextChangedListener(dateWatcher);
        edtMonth.addTextChangedListener(dateWatcher);
        edtYear.addTextChangedListener(dateWatcher);

        // Tự động chuyển con trỏ giữa Ngày -> Tháng -> Năm
        edtDay.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 2) {
                    edtMonth.requestFocus();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        edtMonth.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 2) {
                    edtYear.requestFocus();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Xử lý phím xóa (Backspace) lùi ô
        edtMonth.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (edtMonth.getText().toString().isEmpty()) {
                    edtDay.requestFocus();
                }
            }
            return false;
        });

        edtYear.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (edtYear.getText().toString().isEmpty()) {
                    edtMonth.requestFocus();
                }
            }
            return false;
        });
    }

    private void calculateAndDisplayAge() {
        String dayStr = edtDay.getText().toString().trim();
        String monthStr = edtMonth.getText().toString().trim();
        String yearStr = edtYear.getText().toString().trim();

        if (dayStr.length() < 1 || monthStr.length() < 1 || yearStr.length() < 4) {
            tvAgeResult.setText("Nhập ngày sinh để tính tuổi");
            tvAgeResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            return;
        }

        try {
            int day = Integer.parseInt(dayStr);
            int month = Integer.parseInt(monthStr);
            int year = Integer.parseInt(yearStr);

            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            if (day < 1 || day > 31 || month < 1 || month > 12 || year < 1920 || year > currentYear) {
                tvAgeResult.setText("Ngày sinh không hợp lệ");
                tvAgeResult.setTextColor(0xFFEF1254);
                return;
            }

            Calendar birthDate = Calendar.getInstance();
            birthDate.setLenient(false);
            birthDate.set(year, month - 1, day);
            birthDate.getTime();

            Calendar today = Calendar.getInstance();
            int age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }

            if (age < 18) {
                tvAgeResult.setText("Bạn chưa đủ 18 tuổi (Bạn " + age + " tuổi)");
                tvAgeResult.setTextColor(0xFFEF1254);
            } else {
                tvAgeResult.setText("Bạn " + age + " tuổi");
                tvAgeResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            }
        } catch (Exception e) {
            tvAgeResult.setText("Nhập ngày sinh để tính tuổi");
            tvAgeResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void checkAndContinue() {
        String dayStr = edtDay.getText().toString().trim();
        String monthStr = edtMonth.getText().toString().trim();
        String yearStr = edtYear.getText().toString().trim();

        if (dayStr.isEmpty() || monthStr.isEmpty() || yearStr.length() < 4) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ Ngày, Tháng, Năm sinh", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int day = Integer.parseInt(dayStr);
            int month = Integer.parseInt(monthStr);
            int year = Integer.parseInt(yearStr);

            Calendar birthDate = Calendar.getInstance();
            birthDate.setLenient(false);
            birthDate.set(year, month - 1, day);
            birthDate.getTime();

            Calendar today = Calendar.getInstance();
            int age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }

            if (age < 18) {
                Toast.makeText(this, "Bạn phải từ 18 tuổi trở lên để sử dụng Date Zone", Toast.LENGTH_LONG).show();
                return;
            }

            // Chuyển sang lg4_activity_gender
            android.content.Intent intent = new android.content.Intent(BirthdayActivity.this, GenderActivity.class);
            intent.putExtra("EXTRA_USER_AGE", age);
            ApiUi.saveProfile(this, ApiUi.data("birthday", String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month, day)), intent);

        } catch (Exception e) {
            Toast.makeText(this, "Ngày sinh không hợp lệ", Toast.LENGTH_SHORT).show();
        }
    }
}
