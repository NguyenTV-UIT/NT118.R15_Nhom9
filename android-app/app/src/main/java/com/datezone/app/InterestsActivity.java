package com.datezone.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class InterestsActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_INTERESTS = "EXTRA_SELECTED_INTERESTS";
    public static final String EXTRA_IS_EDIT = "EXTRA_IS_EDIT";

    private TextView tvInterestCount;
    private ChipGroup chipGroupInterests;
    private boolean isEditMode = false;
    private boolean choicesLoaded;
    private ArrayList<String> pendingSelected = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg9_activity_interests);

        View root = findViewById(R.id.interestsRoot);
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

        tvInterestCount = findViewById(R.id.tvInterestCount);
        chipGroupInterests = findViewById(R.id.chipGroupInterests);

        chipGroupInterests.clearCheck();

        ArrayList<String> initialSelected = null;
        if (getIntent() != null) {
            if (getIntent().hasExtra(EXTRA_IS_EDIT)) {
                isEditMode = getIntent().getBooleanExtra(EXTRA_IS_EDIT, false);
            }
            if (getIntent().hasExtra(EXTRA_SELECTED_INTERESTS)) {
                initialSelected = getIntent().getStringArrayListExtra(EXTRA_SELECTED_INTERESTS);
                if (initialSelected != null) isEditMode = true;
            }
        }

        if (isEditMode) {
            View progress = findViewById(R.id.progressProfile);
            View tvStep = findViewById(R.id.tvStep);
            if (progress != null) progress.setVisibility(View.GONE);
            if (tvStep != null) tvStep.setVisibility(View.GONE);
        }

        if (initialSelected != null) {
            for (int i = 0; i < chipGroupInterests.getChildCount(); i++) {
                View child = chipGroupInterests.getChildAt(i);
                if (child instanceof Chip) {
                    Chip chip = (Chip) child;
                    if (initialSelected.contains(chip.getText().toString())) {
                        chip.setChecked(true);
                    }
                }
            }
        }

        setupChipListeners();
        updateSelectedCount();

        // Nút Quay lại
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Lưu thay đổi / Tiếp tục
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
        ArrayList<String> restored = savedInstanceState == null ? initialSelected : savedInstanceState.getStringArrayList("interestDraft");
        loadInterestChoices(restored == null ? new ArrayList<>() : restored);
    }

    private void loadInterestChoices(ArrayList<String> selected) {
        choicesLoaded = false;
        pendingSelected = new ArrayList<>(selected);
        findViewById(R.id.btnContinue).setEnabled(false);
        chipGroupInterests.removeAllViews();
        tvInterestCount.setText("Đang tải sở thích…");
        new com.datezone.app.api.ApiClient(this).request(this, "GET", "users/interests", null,
                new com.datezone.app.api.ApiClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                        org.json.JSONArray choices = response.getJSONArray("data");
                        for (int i = 0; i < choices.length(); i++) {
                            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_profile_interest_chip, chipGroupInterests, false);
                            chip.setId(View.generateViewId());
                            chip.setText(choices.getJSONObject(i).getString("name"));
                            chip.setCheckable(true);
                            chip.setClickable(true);
                            chip.setFocusable(true);
                            chip.setChecked(selected.contains(chip.getText().toString()));
                            chip.setOnCheckedChangeListener((button, checked) -> {
                                if (checked && getSelectedInterests().size() > 20) {
                                    chip.setChecked(false);
                                    Toast.makeText(InterestsActivity.this, "Chọn tối đa 20 sở thích", Toast.LENGTH_SHORT).show();
                                }
                                updateSelectedCount();
                            });
                            chipGroupInterests.addView(chip);
                        }
                        updateSelectedCount();
                        choicesLoaded = true;
                        findViewById(R.id.btnContinue).setEnabled(true);
                        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
                    }
                    @Override public void onError(String message) {
                        tvInterestCount.setText(message);
                        findViewById(R.id.btnContinue).setEnabled(true);
                        findViewById(R.id.btnContinue).setOnClickListener(v -> loadInterestChoices(selected));
                    }
                });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putStringArrayList("interestDraft", choicesLoaded ? new ArrayList<>(getSelectedInterests()) : pendingSelected);
        super.onSaveInstanceState(state);
    }

    private void setupChipListeners() {
        for (int i = 0; i < chipGroupInterests.getChildCount(); i++) {
            View child = chipGroupInterests.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> updateSelectedCount());
            }
        }
    }

    private void updateSelectedCount() {
        int count = getSelectedInterests().size();
        tvInterestCount.setText("Đã chọn " + count + " sở thích · Chọn ít nhất 3");
    }

    private List<String> getSelectedInterests() {
        List<String> selectedList = new ArrayList<>();
        for (int i = 0; i < chipGroupInterests.getChildCount(); i++) {
            View child = chipGroupInterests.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.isChecked()) {
                    selectedList.add(chip.getText().toString());
                }
            }
        }
        return selectedList;
    }

    private void checkAndContinue() {
        List<String> selected = getSelectedInterests();

        if (selected.size() < 3) {
            Toast.makeText(this, "Vui lòng chọn ít nhất 3 sở thích", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isEditMode) {
            android.content.Intent resultIntent = new android.content.Intent();
            resultIntent.putStringArrayListExtra(EXTRA_SELECTED_INTERESTS, new java.util.ArrayList<>(selected));
            setResult(RESULT_OK, resultIntent);
            finish();
        } else {
            android.content.Intent intent = new android.content.Intent(InterestsActivity.this, RelationshipGoalActivity.class);
            intent.putStringArrayListExtra("EXTRA_INTERESTS", new java.util.ArrayList<>(selected));
            com.datezone.app.api.ApiUi.save(this, "users/me/interests",
                    com.datezone.app.api.ApiUi.data("interestNames", new org.json.JSONArray(selected)), intent);
        }
    }
}
