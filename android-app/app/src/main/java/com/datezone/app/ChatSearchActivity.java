package com.datezone.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class ChatSearchActivity extends AppCompatActivity {

    public static class SearchItemModel {
        String name;
        String message;
        String time;
        int photoResId;
        int unreadCount;
        boolean isCloseFriend;
        int msgCount;
        long matchId;
        String photoUrl = "";
        org.json.JSONObject user;

        public SearchItemModel(String name, String message, String time, int photoResId, int unreadCount, boolean isCloseFriend, int msgCount) {
            this.name = name;
            this.message = message;
            this.time = time;
            this.photoResId = photoResId;
            this.unreadCount = unreadCount;
            this.isCloseFriend = isCloseFriend;
            this.msgCount = msgCount;
        }
    }

    private EditText edtSearchQuery;
    private ImageView btnSearchClear;
    private ChipGroup chipGroupFilter;
    private Chip chipFilterAll;
    private Chip chipFilterCloseFriends;
    private TextView tvSearchResultCount;
    private LinearLayout layoutSearchResults;

    private final List<SearchItemModel> masterList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.chat_activity_search);

        View root = findViewById(R.id.chatSearchRoot);
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
        setupSearchAndFilterListeners();
        filterAndDisplayResults();
        loadChats();
    }

    /** GET /chats - tải mọi cuộc trò chuyện 1 lần, lọc theo tên ngay trên máy khi gõ. */
    private void loadChats() {
        new com.datezone.app.api.ApiClient(this).request(this, "GET", "chats?limit=200", null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                org.json.JSONArray items = response.getJSONArray("data");
                masterList.clear();
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject chat = items.getJSONObject(i);
                    org.json.JSONObject user = chat.getJSONObject("user");
                    org.json.JSONObject last = chat.optJSONObject("lastMessage");
                    org.json.JSONObject intimacy = chat.optJSONObject("intimacy");
                    SearchItemModel item = new SearchItemModel(
                            com.datezone.app.api.ChatUi.text(user, "displayName"),
                            com.datezone.app.api.ChatUi.text(last, "preview"),
                            last == null ? "" : com.datezone.app.api.ChatUi.timeLabel(last.optString("createdAt")),
                            0, chat.optInt("unreadCount"),
                            intimacy != null && intimacy.optBoolean("isCloseFriend"),
                            intimacy == null ? 0 : intimacy.optInt("score"));
                    item.matchId = chat.getLong("matchId");
                    item.photoUrl = com.datezone.app.api.ChatUi.avatar(user);
                    item.user = user;
                    masterList.add(item);
                }
                filterAndDisplayResults();
            }
            @Override public void onError(String message) {
                android.widget.Toast.makeText(ChatSearchActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Bỏ dấu tiếng Việt để gõ "binh" vẫn tìm ra "Bình". */
    private static String fold(String text) {
        if (text == null) return "";
        String plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.replace('đ', 'd').replace('Đ', 'D').toLowerCase();
    }

    private void initViews() {
        edtSearchQuery = findViewById(R.id.edtSearchQuery);
        btnSearchClear = findViewById(R.id.btnSearchClear);
        chipGroupFilter = findViewById(R.id.chipGroupSearchFilter);
        chipFilterAll = findViewById(R.id.chipFilterAll);
        chipFilterCloseFriends = findViewById(R.id.chipFilterCloseFriends);
        tvSearchResultCount = findViewById(R.id.tvSearchResultCount);
        layoutSearchResults = findViewById(R.id.layoutSearchResults);
    }

    private void initData() {
        masterList.clear();
        masterList.add(new SearchItemModel("Nguyên", "Sounds like a plan! See you at 7.", "10:42 AM", R.drawable.discovery_photo_1, 3, true, 12));
        masterList.add(new SearchItemModel("Grace", "Any plan for the weekend? Let's catch up soon.", "Hôm qua", R.drawable.discovery_photo_1, 0, false, 2));
        masterList.add(new SearchItemModel("Chloe", "No plan yet, let me check. Thanks for the recommendation!", "Chủ nhật", R.drawable.discovery_photo_5, 0, false, 4));
        masterList.add(new SearchItemModel("Abigail", "I'm really looking forward to it.", "Hôm qua", R.drawable.discovery_photo_2, 1, false, 5));
        masterList.add(new SearchItemModel("Penelope", "I'll send you the details.", "Thứ hai", R.drawable.discovery_photo_4, 0, true, 22));
        masterList.add(new SearchItemModel("Elizabeth", "Hey! How was your day?", "Thứ ba", R.drawable.discovery_photo_3, 0, false, 8));
    }

    private void setupSearchAndFilterListeners() {
        // Nút Quay lại
        findViewById(R.id.btnSearchBack).setOnClickListener(v -> finish());

        // Nút Xóa từ khóa
        if (btnSearchClear != null) {
            btnSearchClear.setOnClickListener(v -> {
                if (edtSearchQuery != null) {
                    edtSearchQuery.setText("");
                }
            });
        }

        // Theo dõi sự thay đổi chữ trong ô tìm kiếm
        if (edtSearchQuery != null) {
            edtSearchQuery.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (btnSearchClear != null) {
                        btnSearchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                    filterAndDisplayResults();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Tải lọc theo chip (Tất cả, Thân thiết, Chưa đọc)
        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> filterAndDisplayResults());
        }
    }

    private void filterAndDisplayResults() {
        if (layoutSearchResults == null || tvSearchResultCount == null) return;

        String query = edtSearchQuery != null ? edtSearchQuery.getText().toString().trim().toLowerCase() : "";
        boolean filterCloseFriendsOnly = chipFilterCloseFriends != null && chipFilterCloseFriends.isChecked();

        List<SearchItemModel> filteredList = new ArrayList<>();

        for (SearchItemModel item : masterList) {
            if (filterCloseFriendsOnly && !item.isCloseFriend) continue;

            if (query.isEmpty()) {
                filteredList.add(item);
            } else {
                boolean matchesName = item.name != null && (item.name.toLowerCase().contains(query) || fold(item.name).contains(fold(query)));
                if (matchesName) {
                    filteredList.add(item);
                }
            }
        }

        if (!query.isEmpty() || filterCloseFriendsOnly) {
            tvSearchResultCount.setVisibility(View.VISIBLE);
            tvSearchResultCount.setText(filteredList.size() + " NGƯỜI TÌM THẤY");
        } else {
            tvSearchResultCount.setVisibility(View.GONE);
        }
        layoutSearchResults.removeAllViews();

        int densityDp = (int) getResources().getDisplayMetrics().density;
        int brandPrimaryColor = ContextCompat.getColor(this, R.color.brand_primary);

        for (SearchItemModel item : filteredList) {
            LinearLayout itemRow = new LinearLayout(this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    80 * densityDp
            );
            itemRow.setLayoutParams(rowParams);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(0, 10 * densityDp, 0, 10 * densityDp);
            itemRow.setClickable(true);
            itemRow.setFocusable(true);
            itemRow.setBackgroundColor(android.graphics.Color.TRANSPARENT);

            // Khung Avatar hình tròn kích thước to (64dp) với viền hồng
            FrameLayout frameAvatar = new FrameLayout(this);
            FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(64 * densityDp, 64 * densityDp);
            frameAvatar.setLayoutParams(avatarParams);

            ShapeableImageView ivPhoto = new ShapeableImageView(this);
            FrameLayout.LayoutParams photoParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            ivPhoto.setLayoutParams(photoParams);
            ivPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (item.photoUrl != null && !item.photoUrl.isEmpty()) com.datezone.app.api.RemoteImages.load(ivPhoto, item.photoUrl);
            else ivPhoto.setImageResource(item.photoResId != 0 ? item.photoResId : R.drawable.discovery_profile_placeholder);

            com.google.android.material.shape.ShapeAppearanceModel shapeModel = ivPhoto.getShapeAppearanceModel().toBuilder()
                    .setAllCorners(com.google.android.material.shape.CornerFamily.ROUNDED, 32 * densityDp)
                    .build();
            ivPhoto.setShapeAppearanceModel(shapeModel);
            ivPhoto.setStrokeColor(android.content.res.ColorStateList.valueOf(brandPrimaryColor));
            ivPhoto.setStrokeWidth(2.5f * densityDp);
            ivPhoto.setPadding(2 * densityDp, 2 * densityDp, 2 * densityDp, 2 * densityDp);

            frameAvatar.addView(ivPhoto);

            // Tên người dùng chữ to in đậm (18sp)
            TextView tvName = new TextView(this);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            nameParams.setMarginStart(18 * densityDp);
            tvName.setLayoutParams(nameParams);
            tvName.setText(highlightQuery(item.name, query, brandPrimaryColor));
            tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvName.setTextSize(18f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

            itemRow.addView(frameAvatar);
            itemRow.addView(tvName);

            // Mở khung chat khi chọn kết quả tìm kiếm
            itemRow.setOnClickListener(v -> {
                if (item.matchId > 0) {
                    startActivity(com.datezone.app.api.ChatUi.chatIntent(ChatSearchActivity.this, item.matchId, item.user));
                    return;
                }
                Intent intent = new Intent(ChatSearchActivity.this, ChatDetailActivity.class);
                intent.putExtra(ChatDetailActivity.EXTRA_NAME, item.name);
                intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_RES_ID, item.photoResId);
                intent.putExtra(ChatDetailActivity.EXTRA_INITIAL_MSG_COUNT, item.msgCount);
                startActivity(intent);
            });

            layoutSearchResults.addView(itemRow);
        }
    }

    private CharSequence highlightQuery(String text, String query, int highlightColor) {
        if (text == null) return "";
        if (query == null || query.isEmpty()) return text;

        String lowerText = text.toLowerCase();
        int index = lowerText.indexOf(query);
        if (index < 0) return text;

        SpannableString spannable = new SpannableString(text);
        while (index >= 0) {
            int end = index + query.length();
            spannable.setSpan(new ForegroundColorSpan(highlightColor), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new android.text.style.BackgroundColorSpan(Color.parseColor("#FFE8EC")), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            index = lowerText.indexOf(query, end);
        }
        return spannable;
    }
}
