package com.datezone.app;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import com.datezone.app.api.ApiClient;
import org.json.JSONArray;
import org.json.JSONObject;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;

public class FiltersActivity extends AppCompatActivity {
    public static final String EXTRA_SEARCH_QUERY = "discoverySearchQuery";
    private final java.util.Set<String> selectedInterestIds = new java.util.HashSet<>();

    private MaterialButtonToggleGroup groupGender;
    private ChipGroup groupOrientation;
    private Slider sliderDistance;
    private RangeSlider sliderAge;
    private RangeSlider sliderHeight;
    private RangeSlider sliderWeight;

    private TextView tvDistanceValue;
    private TextView tvAgeValue;
    private TextView tvHeightValue;
    private TextView tvWeightValue;

    private LinearLayout rowFiltersInterests;
    private ImageView ivFiltersInterestsArrow;
    private LinearLayout containerFiltersInterestsExpanded;
    private ChipGroup chipGroupFiltersInterests;
    private TextView tvInterestsSummary;

    private LinearLayout rowFiltersRelationship;
    private ImageView ivFiltersRelationshipArrow;
    private LinearLayout containerFiltersRelationshipExpanded;
    private ChipGroup chipGroupFiltersRelationship;
    private TextView tvRelationshipSummary;

    private LinearLayout rowFiltersZodiac;
    private ImageView ivFiltersZodiacArrow;
    private LinearLayout containerFiltersZodiacExpanded;
    private ChipGroup chipGroupFiltersZodiac;
    private Chip chipFiltersZodiacAny;
    private TextView tvZodiacSummary;

    private LinearLayout rowFiltersSmoking;
    private ImageView ivFiltersSmokingArrow;
    private LinearLayout containerFiltersSmokingExpanded;
    private ChipGroup chipGroupFiltersSmoking;
    private Chip chipFiltersSmokingAny;
    private TextView tvSmokingSummary;

    private LinearLayout rowFiltersDrinking;
    private ImageView ivFiltersDrinkingArrow;
    private LinearLayout containerFiltersDrinkingExpanded;
    private ChipGroup chipGroupFiltersDrinking;
    private Chip chipFiltersDrinkingAny;
    private TextView tvDrinkingSummary;

    private LinearLayout rowFiltersExercise;
    private ImageView ivFiltersExerciseArrow;
    private LinearLayout containerFiltersExerciseExpanded;
    private ChipGroup chipGroupFiltersExercise;
    private Chip chipFiltersExerciseAny;
    private TextView tvExerciseSummary;

    private LinearLayout rowFiltersEducation;
    private ImageView ivFiltersEducationArrow;
    private LinearLayout containerFiltersEducationExpanded;
    private ChipGroup chipGroupFiltersEducation;
    private Chip chipFiltersEducationAny;
    private TextView tvEducationSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.discovery_activity_filters);

        View root = findViewById(R.id.filtersRoot);
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

        initViews();
        setupSliderListeners();
        setupExpandableInterests();
        setupExpandableRelationshipGoal();
        setupExpandableZodiac();
        setupExpandableLifestyleSections();
        setupActionButtons();
        disableUnavailableFilters();
        String query = savedInstanceState == null ? getIntent().getStringExtra(EXTRA_SEARCH_QUERY)
                : savedInstanceState.getString(EXTRA_SEARCH_QUERY);
        restoreSearchQuery(query);
        loadInterests();
    }

    private void disableUnavailableFilters() {
        // Hồ sơ trên máy chủ chưa có xu hướng tính dục / quận huyện nên 3 mục này vẫn tắt.
        setEnabledRecursively(groupOrientation, false);
        setEnabledRecursively(findViewById(R.id.btnFiltersLocation), false);
        setEnabledRecursively(findViewById(R.id.groupFiltersDistancePresets), false);
        trackAdvancedFilters();
    }

    // Bộ lọc nâng cao (chiều cao, cân nặng, mục tiêu, cung hoàng đạo, lối sống, học vấn) đã có trên máy chủ
    // (GET /home/search). Chỉ gửi mục nào người dùng THỰC SỰ chạm vào, để giá trị mặc định của màn hình
    // không âm thầm thu hẹp kết quả.
    private boolean heightTouched, weightTouched;
    private final java.util.HashSet<ChipGroup> touchedGroups = new java.util.HashSet<>();

    private void trackAdvancedFilters() {
        if (sliderHeight != null) sliderHeight.addOnChangeListener((slider, value, fromUser) -> { if (fromUser) heightTouched = true; });
        if (sliderWeight != null) sliderWeight.addOnChangeListener((slider, value, fromUser) -> { if (fromUser) weightTouched = true; });
        ChipGroup[] groups = {chipGroupFiltersRelationship, chipGroupFiltersZodiac, chipGroupFiltersSmoking,
                chipGroupFiltersDrinking, chipGroupFiltersExercise, chipGroupFiltersEducation};
        for (ChipGroup group : groups) {
            if (group == null) continue;
            for (int i = 0; i < group.getChildCount(); i++) {
                group.getChildAt(i).setOnTouchListener((view, event) -> {
                    touchedGroups.add(group);
                    return false; // không chặn thao tác bấm chip
                });
            }
        }
    }

    /** Thêm các chip đang chọn của 1 nhóm vào query (lặp tham số: zodiac=Sư Tử&zodiac=Xử Nữ). Máy chủ nhận nhãn tiếng Việt. */
    private void appendChips(Uri.Builder query, String key, ChipGroup group, Chip anyChip) {
        if (group == null || !touchedGroups.contains(group)) return;
        if (anyChip != null && anyChip.isChecked()) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && child != anyChip && ((Chip) child).isChecked())
                query.appendQueryParameter(key, ((Chip) child).getText().toString());
        }
    }

    private void setEnabledRecursively(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEnabledRecursively(group.getChildAt(i), enabled);
        }
        view.setAlpha(enabled ? 1f : 0.6f);
    }

    private void restoreSearchQuery(String query) {
        if (query == null || query.isEmpty()) return;
        Uri uri = Uri.parse("https://filters.local/?" + query);
        String gender = uri.getQueryParameter("gender");
        groupGender.check("male".equals(gender) ? R.id.btnFiltersMen : "everyone".equals(gender)
                ? R.id.btnFiltersEveryone : R.id.btnFiltersWomen);
        try {
            sliderDistance.setValue(Float.parseFloat(uri.getQueryParameter("maxDistanceKm")));
            sliderAge.setValues(Float.parseFloat(uri.getQueryParameter("minAge")), Float.parseFloat(uri.getQueryParameter("maxAge")));
        } catch (Exception ignored) { /* Keep valid defaults if no previous query exists. */ }
        String ids = uri.getQueryParameter("interestIds");
        if (ids != null && !ids.isEmpty()) java.util.Collections.addAll(selectedInterestIds, ids.split(","));
    }

    private void loadInterests() {
        chipGroupFiltersInterests.removeAllViews();
        tvInterestsSummary.setText("Đang tải sở thích…");
        new ApiClient(this).request(this, "GET", "users/interests", null, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) throws Exception {
                JSONArray interests = response.getJSONArray("data");
                for (int i = 0; i < interests.length(); i++) {
                    JSONObject interest = interests.getJSONObject(i);
                    String id = interest.getString("id");
                    Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_profile_interest_chip, chipGroupFiltersInterests, false);
                    chip.setId(View.generateViewId());
                    chip.setText(interest.getString("name"));
                    chip.setCheckable(true);
                    chip.setClickable(true);
                    chip.setFocusable(true);
                    chip.setTag(id);
                    chip.setChecked(selectedInterestIds.contains(id));
                    chip.setOnCheckedChangeListener((button, checked) -> {
                        if (checked && selectedInterestIds.size() >= 10) {
                            chip.setChecked(false);
                            Toast.makeText(FiltersActivity.this, "Chọn tối đa 10 sở thích", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (checked) selectedInterestIds.add(id); else selectedInterestIds.remove(id);
                        updateInterestsSummary();
                    });
                    chipGroupFiltersInterests.addView(chip);
                }
                updateInterestsSummary();
            }
            @Override public void onError(String message) {
                tvInterestsSummary.setText("Chưa tải được sở thích");
                Toast.makeText(FiltersActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String buildSearchQuery() {
        int selected = groupGender.getCheckedButtonId();
        String gender = selected == R.id.btnFiltersMen ? "male" : selected == R.id.btnFiltersEveryone ? "everyone" : "female";
        Uri.Builder query = new Uri.Builder().appendQueryParameter("gender", gender)
                .appendQueryParameter("minAge", String.valueOf(Math.round(sliderAge.getValues().get(0))))
                .appendQueryParameter("maxAge", String.valueOf(Math.round(sliderAge.getValues().get(1))))
                .appendQueryParameter("maxDistanceKm", String.valueOf(Math.round(sliderDistance.getValue())))
                .appendQueryParameter("sort", "score").appendQueryParameter("limit", "20");
        if (!selectedInterestIds.isEmpty()) query.appendQueryParameter("interestIds", android.text.TextUtils.join(",", new java.util.TreeSet<>(selectedInterestIds)));
        if (heightTouched && sliderHeight != null && sliderHeight.getValues().size() >= 2) {
            query.appendQueryParameter("minHeight", String.valueOf(Math.round(sliderHeight.getValues().get(0))));
            query.appendQueryParameter("maxHeight", String.valueOf(Math.round(sliderHeight.getValues().get(1))));
        }
        if (weightTouched && sliderWeight != null && sliderWeight.getValues().size() >= 2) {
            query.appendQueryParameter("minWeight", String.valueOf(Math.round(sliderWeight.getValues().get(0))));
            query.appendQueryParameter("maxWeight", String.valueOf(Math.round(sliderWeight.getValues().get(1))));
        }
        appendChips(query, "relationshipGoal", chipGroupFiltersRelationship, null);
        appendChips(query, "zodiac", chipGroupFiltersZodiac, chipFiltersZodiacAny);
        appendChips(query, "smoking", chipGroupFiltersSmoking, chipFiltersSmokingAny);
        appendChips(query, "drinking", chipGroupFiltersDrinking, chipFiltersDrinkingAny);
        appendChips(query, "exercise", chipGroupFiltersExercise, chipFiltersExerciseAny);
        appendChips(query, "education", chipGroupFiltersEducation, chipFiltersEducationAny);
        return query.build().getEncodedQuery();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_SEARCH_QUERY, buildSearchQuery());
        super.onSaveInstanceState(state);
    }

    private void initViews() {
        groupGender = findViewById(R.id.groupFiltersGender);
        groupOrientation = findViewById(R.id.groupFiltersOrientation);
        sliderDistance = findViewById(R.id.sliderFiltersDistance);
        sliderAge = findViewById(R.id.sliderFiltersAge);
        sliderHeight = findViewById(R.id.sliderFiltersHeight);
        sliderWeight = findViewById(R.id.sliderFiltersWeight);

        tvDistanceValue = findViewById(R.id.tvFiltersDistanceValue);
        tvAgeValue = findViewById(R.id.tvFiltersAgeValue);
        tvHeightValue = findViewById(R.id.tvFiltersHeightValue);
        tvWeightValue = findViewById(R.id.tvFiltersWeightValue);

        rowFiltersInterests = findViewById(R.id.rowFiltersInterests);
        ivFiltersInterestsArrow = findViewById(R.id.ivFiltersInterestsArrow);
        containerFiltersInterestsExpanded = findViewById(R.id.containerFiltersInterestsExpanded);
        chipGroupFiltersInterests = findViewById(R.id.chipGroupFiltersInterests);
        tvInterestsSummary = findViewById(R.id.tvFiltersInterestsSummary);

        rowFiltersRelationship = findViewById(R.id.rowFiltersRelationship);
        ivFiltersRelationshipArrow = findViewById(R.id.ivFiltersRelationshipArrow);
        containerFiltersRelationshipExpanded = findViewById(R.id.containerFiltersRelationshipExpanded);
        chipGroupFiltersRelationship = findViewById(R.id.chipGroupFiltersRelationship);
        tvRelationshipSummary = findViewById(R.id.tvFiltersRelationshipSummary);

        rowFiltersZodiac = findViewById(R.id.rowFiltersZodiac);
        ivFiltersZodiacArrow = findViewById(R.id.ivFiltersZodiacArrow);
        containerFiltersZodiacExpanded = findViewById(R.id.containerFiltersZodiacExpanded);
        chipGroupFiltersZodiac = findViewById(R.id.chipGroupFiltersZodiac);
        chipFiltersZodiacAny = findViewById(R.id.chipFiltersZodiacAny);
        tvZodiacSummary = findViewById(R.id.tvFiltersZodiacSummary);

        rowFiltersSmoking = findViewById(R.id.rowFiltersSmoking);
        ivFiltersSmokingArrow = findViewById(R.id.ivFiltersSmokingArrow);
        containerFiltersSmokingExpanded = findViewById(R.id.containerFiltersSmokingExpanded);
        chipGroupFiltersSmoking = findViewById(R.id.chipGroupFiltersSmoking);
        chipFiltersSmokingAny = findViewById(R.id.chipFiltersSmokingAny);
        tvSmokingSummary = findViewById(R.id.tvFiltersSmokingSummary);

        rowFiltersDrinking = findViewById(R.id.rowFiltersDrinking);
        ivFiltersDrinkingArrow = findViewById(R.id.ivFiltersDrinkingArrow);
        containerFiltersDrinkingExpanded = findViewById(R.id.containerFiltersDrinkingExpanded);
        chipGroupFiltersDrinking = findViewById(R.id.chipGroupFiltersDrinking);
        chipFiltersDrinkingAny = findViewById(R.id.chipFiltersDrinkingAny);
        tvDrinkingSummary = findViewById(R.id.tvFiltersDrinkingSummary);

        rowFiltersExercise = findViewById(R.id.rowFiltersExercise);
        ivFiltersExerciseArrow = findViewById(R.id.ivFiltersExerciseArrow);
        containerFiltersExerciseExpanded = findViewById(R.id.containerFiltersExerciseExpanded);
        chipGroupFiltersExercise = findViewById(R.id.chipGroupFiltersExercise);
        chipFiltersExerciseAny = findViewById(R.id.chipFiltersExerciseAny);
        tvExerciseSummary = findViewById(R.id.tvFiltersExerciseSummary);

        rowFiltersEducation = findViewById(R.id.rowFiltersEducation);
        ivFiltersEducationArrow = findViewById(R.id.ivFiltersEducationArrow);
        containerFiltersEducationExpanded = findViewById(R.id.containerFiltersEducationExpanded);
        chipGroupFiltersEducation = findViewById(R.id.chipGroupFiltersEducation);
        chipFiltersEducationAny = findViewById(R.id.chipFiltersEducationAny);
        tvEducationSummary = findViewById(R.id.tvFiltersEducationSummary);
    }

    private void setupSliderListeners() {
        if (sliderDistance != null) {
            sliderDistance.addOnChangeListener((slider, value, fromUser) -> {
                if (tvDistanceValue != null) {
                    tvDistanceValue.setText((int) value + " km");
                }
            });
        }

        if (sliderAge != null) {
            sliderAge.addOnChangeListener((slider, value, fromUser) -> {
                if (tvAgeValue != null && slider.getValues().size() >= 2) {
                    int minAge = slider.getValues().get(0).intValue();
                    int maxAge = slider.getValues().get(1).intValue();
                    tvAgeValue.setText(minAge + " - " + maxAge + " tuổi");
                }
            });
        }

        if (sliderHeight != null) {
            sliderHeight.addOnChangeListener((slider, value, fromUser) -> {
                if (tvHeightValue != null && slider.getValues().size() >= 2) {
                    int minH = slider.getValues().get(0).intValue();
                    int maxH = slider.getValues().get(1).intValue();
                    tvHeightValue.setText(minH + " - " + maxH + " cm");
                }
            });
        }

        if (sliderWeight != null) {
            sliderWeight.addOnChangeListener((slider, value, fromUser) -> {
                if (tvWeightValue != null && slider.getValues().size() >= 2) {
                    int minW = slider.getValues().get(0).intValue();
                    int maxW = slider.getValues().get(1).intValue();
                    tvWeightValue.setText(minW + " - " + maxW + " kg");
                }
            });
        }
    }

    private void setupExpandableInterests() {
        if (rowFiltersInterests != null && containerFiltersInterestsExpanded != null) {
            rowFiltersInterests.setOnClickListener(v -> toggleSection(containerFiltersInterestsExpanded, ivFiltersInterestsArrow));
        }

        if (chipGroupFiltersInterests != null) {
            for (int i = 0; i < chipGroupFiltersInterests.getChildCount(); i++) {
                View child = chipGroupFiltersInterests.getChildAt(i);
                if (child instanceof Chip) {
                    child.setOnClickListener(v -> updateInterestsSummary());
                }
            }
        }
        updateInterestsSummary();
    }

    private void updateInterestsSummary() {
        if (chipGroupFiltersInterests == null || tvInterestsSummary == null) return;

        int selectedCount = 0;
        for (int i = 0; i < chipGroupFiltersInterests.getChildCount(); i++) {
            View child = chipGroupFiltersInterests.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                selectedCount++;
            }
        }

        if (selectedCount == 0) {
            tvInterestsSummary.setText("Chọn nhiều • Chưa chọn");
        } else {
            tvInterestsSummary.setText("Chọn nhiều • " + selectedCount + " đã chọn");
        }
    }

    private void setupExpandableRelationshipGoal() {
        if (rowFiltersRelationship != null && containerFiltersRelationshipExpanded != null) {
            rowFiltersRelationship.setOnClickListener(v -> toggleSection(containerFiltersRelationshipExpanded, ivFiltersRelationshipArrow));
        }

        if (chipGroupFiltersRelationship != null) {
            for (int i = 0; i < chipGroupFiltersRelationship.getChildCount(); i++) {
                View child = chipGroupFiltersRelationship.getChildAt(i);
                if (child instanceof Chip) {
                    child.setOnClickListener(v -> updateRelationshipSummary());
                }
            }
        }
        updateRelationshipSummary();
    }

    private void updateRelationshipSummary() {
        if (chipGroupFiltersRelationship == null || tvRelationshipSummary == null) return;

        List<String> selectedGoals = new ArrayList<>();
        for (int i = 0; i < chipGroupFiltersRelationship.getChildCount(); i++) {
            View child = chipGroupFiltersRelationship.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                selectedGoals.add(((Chip) child).getText().toString());
            }
        }

        if (selectedGoals.isEmpty()) {
            tvRelationshipSummary.setText("Chưa chọn");
        } else if (selectedGoals.contains("Mối quan hệ nghiêm túc") && selectedGoals.contains("Tìm hiểu để tiến tới mối quan hệ lâu dài") && selectedGoals.size() == 2) {
            tvRelationshipSummary.setText("Nghiêm túc, Lâu dài");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selectedGoals.size(); i++) {
                String goal = selectedGoals.get(i);
                if (goal.contains("nghiêm túc")) sb.append("Nghiêm túc");
                else if (goal.contains("lâu dài")) sb.append("Lâu dài");
                else if (goal.contains("Hẹn hò")) sb.append("Hẹn hò");
                else if (goal.contains("Kết hôn")) sb.append("Kết hôn");
                else if (goal.contains("Kết bạn")) sb.append("Kết bạn");
                else if (goal.contains("người mới")) sb.append("Kết nối");
                else if (goal.contains("không ràng buộc")) sb.append("Không ràng buộc");
                else sb.append("Chưa xác định");

                if (i < selectedGoals.size() - 1) sb.append(", ");
            }
            tvRelationshipSummary.setText(sb.toString());
        }
    }

    private void setupExpandableZodiac() {
        if (rowFiltersZodiac != null && containerFiltersZodiacExpanded != null) {
            rowFiltersZodiac.setOnClickListener(v -> toggleSection(containerFiltersZodiacExpanded, ivFiltersZodiacArrow));
        }

        if (chipGroupFiltersZodiac != null) {
            for (int i = 0; i < chipGroupFiltersZodiac.getChildCount(); i++) {
                View child = chipGroupFiltersZodiac.getChildAt(i);
                if (child instanceof Chip) {
                    Chip chip = (Chip) child;
                    chip.setOnClickListener(v -> handleSingleOrAnyChipClick(chipGroupFiltersZodiac, chipFiltersZodiacAny, chip, tvZodiacSummary));
                }
            }
        }
        updateSingleOrAnySummary(chipGroupFiltersZodiac, chipFiltersZodiacAny, tvZodiacSummary);
    }

    private void setupExpandableLifestyleSections() {
        // Hút thuốc
        if (rowFiltersSmoking != null && containerFiltersSmokingExpanded != null) {
            rowFiltersSmoking.setOnClickListener(v -> toggleSection(containerFiltersSmokingExpanded, ivFiltersSmokingArrow));
        }
        setupSingleOrAnyGroup(chipGroupFiltersSmoking, chipFiltersSmokingAny, tvSmokingSummary);

        // Uống rượu bia
        if (rowFiltersDrinking != null && containerFiltersDrinkingExpanded != null) {
            rowFiltersDrinking.setOnClickListener(v -> toggleSection(containerFiltersDrinkingExpanded, ivFiltersDrinkingArrow));
        }
        setupSingleOrAnyGroup(chipGroupFiltersDrinking, chipFiltersDrinkingAny, tvDrinkingSummary);

        // Tập luyện
        if (rowFiltersExercise != null && containerFiltersExerciseExpanded != null) {
            rowFiltersExercise.setOnClickListener(v -> toggleSection(containerFiltersExerciseExpanded, ivFiltersExerciseArrow));
        }
        setupSingleOrAnyGroup(chipGroupFiltersExercise, chipFiltersExerciseAny, tvExerciseSummary);

        // Học vấn
        if (rowFiltersEducation != null && containerFiltersEducationExpanded != null) {
            rowFiltersEducation.setOnClickListener(v -> toggleSection(containerFiltersEducationExpanded, ivFiltersEducationArrow));
        }
        setupSingleOrAnyGroup(chipGroupFiltersEducation, chipFiltersEducationAny, tvEducationSummary);
    }

    private void setupSingleOrAnyGroup(ChipGroup group, Chip anyChip, TextView summaryTv) {
        if (group != null) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof Chip) {
                    Chip chip = (Chip) child;
                    chip.setOnClickListener(v -> handleSingleOrAnyChipClick(group, anyChip, chip, summaryTv));
                }
            }
        }
        updateSingleOrAnySummary(group, anyChip, summaryTv);
    }

    private void handleSingleOrAnyChipClick(ChipGroup group, Chip anyChip, Chip clickedChip, TextView summaryTv) {
        if (clickedChip == anyChip) {
            if (anyChip.isChecked()) {
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof Chip && child != anyChip) {
                        ((Chip) child).setChecked(false);
                    }
                }
            } else {
                boolean hasOtherSelected = false;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof Chip && child != anyChip && ((Chip) child).isChecked()) {
                        hasOtherSelected = true;
                        break;
                    }
                }
                if (!hasOtherSelected) {
                    anyChip.setChecked(true);
                }
            }
        } else {
            if (clickedChip.isChecked()) {
                if (anyChip != null) {
                    anyChip.setChecked(false);
                }
            } else {
                boolean hasOtherSelected = false;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof Chip && child != anyChip && ((Chip) child).isChecked()) {
                        hasOtherSelected = true;
                        break;
                    }
                }
                if (!hasOtherSelected && anyChip != null) {
                    anyChip.setChecked(true);
                }
            }
        }
        updateSingleOrAnySummary(group, anyChip, summaryTv);
    }

    private void updateSingleOrAnySummary(ChipGroup group, Chip anyChip, TextView summaryTv) {
        if (group == null || summaryTv == null) return;

        if (anyChip != null && anyChip.isChecked()) {
            summaryTv.setText("Không quan trọng");
            return;
        }

        List<String> selectedOptions = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && child != anyChip && ((Chip) child).isChecked()) {
                selectedOptions.add(((Chip) child).getText().toString());
            }
        }

        if (selectedOptions.isEmpty()) {
            if (anyChip != null) anyChip.setChecked(true);
            summaryTv.setText("Không quan trọng");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selectedOptions.size(); i++) {
                sb.append(selectedOptions.get(i));
                if (i < selectedOptions.size() - 1) sb.append(", ");
            }
            summaryTv.setText(sb.toString());
        }
    }

    private void toggleSection(LinearLayout container, ImageView arrow) {
        if (container == null) return;

        if (container.getVisibility() == View.GONE) {
            container.setVisibility(View.VISIBLE);
            if (arrow != null) arrow.animate().rotation(180f).setDuration(200).start();
        } else {
            container.setVisibility(View.GONE);
            if (arrow != null) arrow.animate().rotation(0f).setDuration(200).start();
        }
    }

    private void setupActionButtons() {
        findViewById(R.id.btnFiltersClose).setOnClickListener(v -> {
            Toast.makeText(this, "Đã hủy chỉnh sửa bộ lọc", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btnFiltersReset).setOnClickListener(v -> resetFiltersToDefault());

        View.OnClickListener applyClickListener = v -> applyAndSaveFilters();

        View btnContinueHeader = findViewById(R.id.btnFiltersContinue);
        if (btnContinueHeader != null) {
            btnContinueHeader.setOnClickListener(applyClickListener);
        }

        View btnApplyFooter = findViewById(R.id.btnFiltersApply);
        if (btnApplyFooter != null) {
            btnApplyFooter.setOnClickListener(applyClickListener);
        }
    }

    private void resetFiltersToDefault() {
        selectedInterestIds.clear();
        heightTouched = false;
        weightTouched = false;
        touchedGroups.clear();
        if (groupGender != null) {
            groupGender.check(R.id.btnFiltersWomen);
        }

        if (groupOrientation != null) {
            groupOrientation.check(R.id.chipFiltersStraight);
        }

        if (sliderDistance != null) {
            sliderDistance.setValue(40f);
            if (tvDistanceValue != null) tvDistanceValue.setText("40 km");
        }

        if (sliderAge != null) {
            sliderAge.setValues(18f, 35f);
            if (tvAgeValue != null) tvAgeValue.setText("18 - 35 tuổi");
        }

        if (sliderHeight != null) {
            sliderHeight.setValues(150f, 185f);
            if (tvHeightValue != null) tvHeightValue.setText("150 - 185 cm");
        }

        if (sliderWeight != null) {
            sliderWeight.setValues(40f, 85f);
            if (tvWeightValue != null) tvWeightValue.setText("40 - 85 kg");
        }

        // Bỏ chọn tất cả sở thích (trở về màu trắng ban đầu)
        if (chipGroupFiltersInterests != null) {
            for (int i = 0; i < chipGroupFiltersInterests.getChildCount(); i++) {
                View child = chipGroupFiltersInterests.getChildAt(i);
                if (child instanceof Chip) {
                    ((Chip) child).setChecked(false);
                }
            }
        }
        if (containerFiltersInterestsExpanded != null) containerFiltersInterestsExpanded.setVisibility(View.GONE);
        if (ivFiltersInterestsArrow != null) ivFiltersInterestsArrow.setRotation(0f);
        updateInterestsSummary();

        // Khôi phục mục "Đang tìm kiếm"
        if (chipGroupFiltersRelationship != null) {
            for (int i = 0; i < chipGroupFiltersRelationship.getChildCount(); i++) {
                View child = chipGroupFiltersRelationship.getChildAt(i);
                if (child instanceof Chip) {
                    String chipText = ((Chip) child).getText().toString();
                    boolean isDefaultSelected = chipText.contains("nghiêm túc") || chipText.contains("lâu dài");
                    ((Chip) child).setChecked(isDefaultSelected);
                }
            }
        }
        if (containerFiltersRelationshipExpanded != null) containerFiltersRelationshipExpanded.setVisibility(View.GONE);
        if (ivFiltersRelationshipArrow != null) ivFiltersRelationshipArrow.setRotation(0f);
        updateRelationshipSummary();

        // Khôi phục Cung hoàng đạo
        resetSingleOrAnyGroup(chipGroupFiltersZodiac, chipFiltersZodiacAny, containerFiltersZodiacExpanded, ivFiltersZodiacArrow, tvZodiacSummary);

        // Khôi phục Hút thuốc, Uống rượu bia, Tập luyện, Học vấn về "Không quan trọng"
        resetSingleOrAnyGroup(chipGroupFiltersSmoking, chipFiltersSmokingAny, containerFiltersSmokingExpanded, ivFiltersSmokingArrow, tvSmokingSummary);
        resetSingleOrAnyGroup(chipGroupFiltersDrinking, chipFiltersDrinkingAny, containerFiltersDrinkingExpanded, ivFiltersDrinkingArrow, tvDrinkingSummary);
        resetSingleOrAnyGroup(chipGroupFiltersExercise, chipFiltersExerciseAny, containerFiltersExerciseExpanded, ivFiltersExerciseArrow, tvExerciseSummary);
        resetSingleOrAnyGroup(chipGroupFiltersEducation, chipFiltersEducationAny, containerFiltersEducationExpanded, ivFiltersEducationArrow, tvEducationSummary);

        Toast.makeText(this, "Đã khôi phục bộ lọc về trạng thái ban đầu", Toast.LENGTH_SHORT).show();
        disableUnavailableFilters();
    }

    private void resetSingleOrAnyGroup(ChipGroup group, Chip anyChip, LinearLayout container, ImageView arrow, TextView summaryTv) {
        if (group != null) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof Chip) {
                    ((Chip) child).setChecked(child == anyChip);
                }
            }
        }
        if (container != null) container.setVisibility(View.GONE);
        if (arrow != null) arrow.setRotation(0f);
        updateSingleOrAnySummary(group, anyChip, summaryTv);
    }

    private void applyAndSaveFilters() {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_SEARCH_QUERY, buildSearchQuery()));
        finish();
    }
}
