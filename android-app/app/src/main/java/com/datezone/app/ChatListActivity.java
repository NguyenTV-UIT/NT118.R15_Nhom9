package com.datezone.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class ChatListActivity extends AppCompatActivity {

    public static class RecentMatchModel {
        String name;
        String age;
        int photoResId;
        String initial;
        int bgColor;
        int indicatorType; // 0: none, 1: red dot, 2: white ring
        int mySentCount;
        int partnerSentCount;

        public RecentMatchModel(String name, String age, int photoResId, String initial, int bgColor, int indicatorType, int mySentCount, int partnerSentCount) {
            this.name = name;
            this.age = age;
            this.photoResId = photoResId;
            this.initial = initial;
            this.bgColor = bgColor;
            this.indicatorType = indicatorType;
            this.mySentCount = mySentCount;
            this.partnerSentCount = partnerSentCount;
        }
    }

    private final List<RecentMatchModel> recentMatchesList = new ArrayList<>();
    private LinearLayout layoutRecentMatches;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.chat_activity_list);

        View root = findViewById(R.id.chatListRoot);
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

                // The root already reserves this space; children must not add it again.
                return insets.inset(bars.left, bars.top, bars.right, bars.bottom);
            });
            ViewCompat.requestApplyInsets(root);
        }

        layoutRecentMatches = findViewById(R.id.layoutRecentMatches);

        // Bấm vào ô tìm kiếm -> Mở ChatSearchActivity
        View edtSearch = findViewById(R.id.edtChatSearch);
        if (edtSearch != null) {
            edtSearch.setFocusable(false);
            edtSearch.setOnClickListener(v -> {
                Intent intent = new Intent(ChatListActivity.this, ChatSearchActivity.class);
                startActivity(intent);
            });
        }

        // Bấm vào huy hiệu 2 thân thiết -> Mở ChatIntimacyActivity
        View badgeCloseFriends = findViewById(R.id.tvCloseFriendsBadge);
        if (badgeCloseFriends != null) {
            badgeCloseFriends.setOnClickListener(v -> {
                Intent intent = new Intent(ChatListActivity.this, ChatIntimacyActivity.class);
                startActivity(intent);
            });
        }

        initRecentMatchesData();
        renderRecentMatchesList();
        setupConversationClicks();
        setupBottomNavigation();
    }

    private void initRecentMatchesData() {
        recentMatchesList.clear();
        // Mảng các tương hợp gần đây (Mới nhất xếp đầu tiên)
        recentMatchesList.add(new RecentMatchModel("Nguyên", "20", R.drawable.discovery_photo_1, null, 0, 1, 20, 20)); // Full Intimacy (20/20)
        recentMatchesList.add(new RecentMatchModel("Mai", "23", 0, "M", Color.parseColor("#D97256"), 2, 0, 0)); // Initial (0/20)
        recentMatchesList.add(new RecentMatchModel("Thảo", "21", 0, "T", Color.parseColor("#BB4A4A"), 2, 0, 0)); // Initial (0/20)
        recentMatchesList.add(new RecentMatchModel("Ngọc", "22", 0, "N", Color.parseColor("#8B684E"), 0, 0, 0));
        recentMatchesList.add(new RecentMatchModel("Hà", "24", 0, "H", Color.parseColor("#596F82"), 0, 0, 0));

        trimRecentMatchesList();
    }

    public void addRecentMatch(RecentMatchModel match) {
        recentMatchesList.add(0, match);
        trimRecentMatchesList();
        renderRecentMatchesList();
    }

    private void trimRecentMatchesList() {
        while (recentMatchesList.size() > 5) {
            recentMatchesList.remove(recentMatchesList.size() - 1);
        }
    }

    private void renderRecentMatchesList() {
        if (layoutRecentMatches == null) return;
        layoutRecentMatches.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int cardWidthPx = (int) (72 * density);
        int cardHeightPx = (int) (100 * density);
        int marginEndPx = (int) (12 * density);
        int brandPrimaryColor = ContextCompat.getColor(this, R.color.brand_primary);

        // Tối đa 5 tương hợp mới nhất xếp đầu tiên
        int displayCount = Math.min(5, recentMatchesList.size());

        for (int i = 0; i < displayCount; i++) {
            RecentMatchModel match = recentMatchesList.get(i);

            // Khung thẻ bo góc 18dp chuẩn theo hình ảnh mẫu
            CardView cardView = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidthPx, cardHeightPx);
            cardParams.setMarginEnd(marginEndPx);
            cardView.setLayoutParams(cardParams);
            cardView.setRadius(18 * density);
            cardView.setCardElevation(3 * density);
            cardView.setPreventCornerOverlap(false);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                if (match.photoResId != 0 || "Mai".equals(match.name) || "Thảo".equals(match.name)) {
                    cardView.setOutlineAmbientShadowColor(brandPrimaryColor);
                }
            }

            FrameLayout frameLayout = new FrameLayout(this);
            frameLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            if (match.photoResId != 0) {
                // Hiển thị ảnh bìa
                ImageView ivPhoto = new ImageView(this);
                ivPhoto.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
                ivPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivPhoto.setImageResource(match.photoResId);
                frameLayout.addView(ivPhoto);
            } else {
                // Hiển thị Chữ cái đầu tiên trên nền màu
                cardView.setCardBackgroundColor(match.bgColor);
                TextView tvInitial = new TextView(this);
                tvInitial.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                ));
                tvInitial.setText(match.initial);
                tvInitial.setTextColor(Color.WHITE);
                tvInitial.setTextSize(32f);
                tvInitial.setTypeface(null, android.graphics.Typeface.BOLD);
                frameLayout.addView(tvInitial);
            }

            // Đáy mờ đệm chữ tên
            View scrimView = new View(this);
            FrameLayout.LayoutParams scrimParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    44 * (int) density,
                    Gravity.BOTTOM
            );
            scrimView.setLayoutParams(scrimParams);
            scrimView.setBackgroundResource(R.drawable.bg_matches_scrim);
            frameLayout.addView(scrimView);

            // Dòng chữ Tên ở đáy thẻ
            TextView tvName = new TextView(this);
            FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );
            nameParams.setMargins(0, 0, 0, 8 * (int) density);
            tvName.setLayoutParams(nameParams);
            tvName.setGravity(Gravity.CENTER);
            tvName.setText(match.name);
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(13f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setMaxLines(1);
            frameLayout.addView(tvName);

            // Đốm / Vòng góc trên bên phải thẻ
            if (match.indicatorType == 1) { // Red dot
                View redDot = new View(this);
                int dotSizePx = (int) (12 * density);
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dotSizePx, dotSizePx);
                dotParams.gravity = Gravity.TOP | Gravity.END;
                dotParams.setMargins(0, 8 * (int) density, 8 * (int) density, 0);
                redDot.setLayoutParams(dotParams);
                redDot.setBackgroundResource(R.drawable.bg_indicator_red_dot);

                frameLayout.addView(redDot);
            } else if (match.indicatorType == 2) { // White ring
                View ringView = new View(this);
                int ringSizePx = (int) (12 * density);
                FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(ringSizePx, ringSizePx);
                ringParams.gravity = Gravity.TOP | Gravity.END;
                ringParams.setMargins(0, 8 * (int) density, 8 * (int) density, 0);
                ringView.setLayoutParams(ringParams);
                ringView.setBackgroundResource(R.drawable.bg_indicator_white_ring);

                frameLayout.addView(ringView);
            }

            cardView.addView(frameLayout);

            // Bấm vào tương hợp gần đây -> Mở cuộc trò chuyện tương ứng (Giữ nguyên thẻ trong danh sách)
            cardView.setOnClickListener(v -> openChatDetail(
                    match.name,
                    match.age,
                    match.photoResId,
                    match.initial,
                    match.bgColor,
                    match.mySentCount,
                    match.partnerSentCount
            ));

            layoutRecentMatches.addView(cardView);
        }
    }

    private void setupConversationClicks() {
        // ITEM 1: Nguyên (Đã thân thiết - 20/20 tin)
        View item1 = findViewById(R.id.itemChat1);
        if (item1 != null) {
            item1.setOnClickListener(v -> openChatDetail("Nguyên", "20", R.drawable.discovery_photo_1, null, 0, 20, 20));
        }

        // ITEM 2: Abigail (Chưa nhắn gì / Mới nhắn - Bạn 9/20, Abigail 7/20 -> 7/20 tin)
        View item2 = findViewById(R.id.itemChat2);
        if (item2 != null) {
            item2.setOnClickListener(v -> openChatDetail("Abigail", "24", 0, "A", Color.parseColor("#9B7853"), 9, 7));
        }

        // ITEM 3: Elizabeth (Chưa nhắn gì / Mới nhắn - Bạn 8/20, Elizabeth 5/20 -> 5/20 tin)
        View item3 = findViewById(R.id.itemChat3);
        if (item3 != null) {
            item3.setOnClickListener(v -> openChatDetail("Elizabeth", "25", 0, "E", Color.parseColor("#E89C6C"), 8, 5));
        }

        // ITEM 4: Penelope (Đã thân thiết - 20/20 tin)
        View item4 = findViewById(R.id.itemChat4);
        if (item4 != null) {
            item4.setOnClickListener(v -> openChatDetail("Penelope", "22", 0, "P", Color.parseColor("#D96452"), 20, 20));
        }

        // ITEM 5: Chloe (Mới đạt 1 nửa - Bạn 15/20, Chloe 14/20 -> 14/20 tin)
        View item5 = findViewById(R.id.itemChat5);
        if (item5 != null) {
            item5.setOnClickListener(v -> openChatDetail("Chloe", "23", 0, "C", Color.parseColor("#8EABB9"), 15, 14));
        }

        // ITEM 6: Grace (Mới đạt 1 nửa - Bạn 14/20, Grace 12/20 -> 12/20 tin)
        View item6 = findViewById(R.id.itemChat6);
        if (item6 != null) {
            item6.setOnClickListener(v -> openChatDetail("Grace", "26", 0, "G", Color.parseColor("#E5A384"), 14, 12));
        }
    }

    private void openChatDetail(String name, String age, int photoResId, String initial, int bgColor, int mySentCount, int partnerSentCount) {
        Intent intent = new Intent(ChatListActivity.this, ChatDetailActivity.class);
        intent.putExtra(ChatDetailActivity.EXTRA_NAME, name);
        intent.putExtra(ChatDetailActivity.EXTRA_AGE, age);
        intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_RES_ID, photoResId);
        intent.putExtra(ChatDetailActivity.EXTRA_INITIAL, initial);
        intent.putExtra(ChatDetailActivity.EXTRA_BG_COLOR, bgColor);
        intent.putExtra(ChatDetailActivity.EXTRA_MY_SENT_COUNT, mySentCount);
        intent.putExtra(ChatDetailActivity.EXTRA_PARTNER_SENT_COUNT, partnerSentCount);
        startActivity(intent);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.chatBottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navDiscoveryChat);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.navDiscoveryExplore) {
                    Intent intent = new Intent(ChatListActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryLikes) {
                    Intent intent = new Intent(ChatListActivity.this, MatchesActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryChat) {
                    return true;
                } else if (itemId == R.id.navDiscoveryProfile) {
                    Intent intent = new Intent(ChatListActivity.this, ProfileHomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                }
                return false;
            });
        }
    }
}
