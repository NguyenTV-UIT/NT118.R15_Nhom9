package com.datezone.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MatchesActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> profileLauncher;

    private RecyclerView rvMatchesList;
    private View layoutMatchesEmpty;
    private MatchAdapter adapter;
    private List<MatchItem> matchItemsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.matches_activity_home);

        View root = findViewById(R.id.matchesRoot);
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

        rvMatchesList = findViewById(R.id.rvMatchesList);
        layoutMatchesEmpty = findViewById(R.id.layoutMatchesEmpty);

        profileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String name = data.getStringExtra(ProfileObject2Activity.EXTRA_NAME);
                        if (name != null) {
                            removeMatchByName(name);
                        }
                    }
                }
        );

        initMatchItemsData();
        setupRecyclerView();
        setupEmptyStateExploreButton();
        setupBottomNavigation();
    }

    private void initMatchItemsData() {
        matchItemsList = new ArrayList<>();

        // Section HÔM NAY
        matchItemsList.add(new MatchItem("HÔM NAY"));

        // Thẻ 1: Nguyễn Minh Anh
        matchItemsList.add(new MatchItem(
                "Nguyễn Minh Anh", "25 tuổi",
                "Chiều cao 165 cm  Cân nặng 55 kg  Giới tính Nữ", "Cách bạn 2,5 km",
                R.drawable.discovery_photo_2,
                new int[]{R.drawable.discovery_photo_2, R.drawable.discovery_photo_3, R.drawable.discovery_photo_1}
        ));

        // Thẻ 2: Trương Vĩnh Nguyên
        matchItemsList.add(new MatchItem(
                "Trương Vĩnh Nguyên", "20 tuổi",
                "Chiều cao 170 cm  Cân nặng 65 kg  Giới tính Nam", "Cách bạn 1,2 km",
                R.drawable.discovery_photo_1,
                new int[]{R.drawable.discovery_photo_1, R.drawable.discovery_photo_2, R.drawable.discovery_photo_3}
        ));

        // Thẻ 3: Lê Hoàng Yến
        matchItemsList.add(new MatchItem(
                "Lê Hoàng Yến", "21 tuổi",
                "Chiều cao 162 cm  Cân nặng 48 kg  Giới tính Nữ", "Cách bạn 0,8 km",
                R.drawable.discovery_photo_5,
                new int[]{R.drawable.discovery_photo_5, R.drawable.discovery_photo_2, R.drawable.discovery_photo_3}
        ));

        // Thẻ 4: Phạm Hoài Nam
        matchItemsList.add(new MatchItem(
                "Phạm Hoài Nam", "24 tuổi",
                "Chiều cao 175 cm  Cân nặng 70 kg  Giới tính Nam", "Cách bạn 4,1 km",
                R.drawable.discovery_photo_4,
                new int[]{R.drawable.discovery_photo_4, R.drawable.discovery_photo_1, R.drawable.discovery_photo_5}
        ));

        // Section HÔM QUA
        matchItemsList.add(new MatchItem("HÔM QUA"));

        // Thẻ 5: Trần Ngọc Linh
        matchItemsList.add(new MatchItem(
                "Trần Ngọc Linh", "22 tuổi",
                "Chiều cao 168 cm  Cân nặng 50 kg  Giới tính Nữ", "Cách bạn 3,8 km",
                R.drawable.discovery_photo_3,
                new int[]{R.drawable.discovery_photo_3, R.drawable.discovery_photo_4, R.drawable.discovery_photo_2}
        ));

        // Thẻ 6: Bạch Ngân
        matchItemsList.add(new MatchItem(
                "Bạch Ngân", "23 tuổi",
                "Chiều cao 164 cm  Cân nặng 51 kg  Giới tính Nữ", "Cách bạn 1,5 km",
                R.drawable.discovery_photo_2,
                new int[]{R.drawable.discovery_photo_2, R.drawable.discovery_photo_5, R.drawable.discovery_photo_1}
        ));
    }

    private void setupRecyclerView() {
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position >= 0 && position < matchItemsList.size()) {
                    return matchItemsList.get(position).getType() == MatchItem.TYPE_HEADER ? 2 : 1;
                }
                return 1;
            }
        });

        rvMatchesList.setLayoutManager(gridLayoutManager);

        adapter = new MatchAdapter(matchItemsList, new MatchAdapter.OnMatchActionListener() {
            @Override
            public void onCardClick(MatchItem item) {
                Intent intent = new Intent(MatchesActivity.this, ProfileObject2Activity.class);
                intent.putExtra(ProfileObject2Activity.EXTRA_NAME, item.getName());
                intent.putExtra(ProfileObject2Activity.EXTRA_AGE, item.getAge());
                intent.putExtra(ProfileObject2Activity.EXTRA_DETAILS, item.getDetails());
                intent.putExtra(ProfileObject2Activity.EXTRA_LOCATION, item.getDistance());
                intent.putExtra(ProfileObject2Activity.EXTRA_PHOTOS, item.getPhotos());
                if (profileLauncher != null) {
                    profileLauncher.launch(intent);
                } else {
                    startActivity(intent);
                }
            }

            @Override
            public void onPassClick(MatchItem item, int position) {
                removeMatchItemAt(position);
                Toast.makeText(MatchesActivity.this, "Đã bỏ qua Lượt thích", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onLikeClick(MatchItem item, int position) {
                removeMatchItemAt(position);

                MatchSuccessDialog matchDialog = new MatchSuccessDialog(
                        MatchesActivity.this,
                        item.getName(),
                        item.getPhotoResId(),
                        new MatchSuccessDialog.OnMatchActionListener() {
                            @Override
                            public void onSayHello(String targetName) {
                                Intent intent = new Intent(MatchesActivity.this, ChatDetailActivity.class);
                                intent.putExtra(ChatDetailActivity.EXTRA_NAME, targetName);
                                intent.putExtra(ChatDetailActivity.EXTRA_PHOTO_RES_ID, item.getPhotoResId());
                                intent.putExtra(ChatDetailActivity.EXTRA_INITIAL_MSG_COUNT, 0);
                                startActivity(intent);
                            }

                            @Override
                            public void onContinue() {
                                // Tiếp tục xem danh sách
                            }
                        }
                );
                matchDialog.show();
            }
        });

        rvMatchesList.setAdapter(adapter);
    }

    private void removeMatchItemAt(int position) {
        if (position < 0 || position >= matchItemsList.size()) return;

        matchItemsList.remove(position);
        adapter.notifyItemRemoved(position);

        cleanUpOrphanHeadersAndCheckEmpty();
    }

    private void removeMatchByName(String name) {
        if (name == null) return;
        for (int i = 0; i < matchItemsList.size(); i++) {
            MatchItem item = matchItemsList.get(i);
            if (item.getType() == MatchItem.TYPE_CARD && item.getName() != null) {
                if (item.getName().contains(name) || name.contains(item.getName())) {
                    removeMatchItemAt(i);
                    break;
                }
            }
        }
    }

    private void cleanUpOrphanHeadersAndCheckEmpty() {
        // Kiểm tra xem có header nào mà không còn card nào phía dưới không
        for (int i = matchItemsList.size() - 1; i >= 0; i--) {
            if (matchItemsList.get(i).getType() == MatchItem.TYPE_HEADER) {
                boolean hasCardBelow = false;
                for (int j = i + 1; j < matchItemsList.size(); j++) {
                    if (matchItemsList.get(j).getType() == MatchItem.TYPE_CARD) {
                        hasCardBelow = true;
                        break;
                    }
                    if (matchItemsList.get(j).getType() == MatchItem.TYPE_HEADER) {
                        break;
                    }
                }
                if (!hasCardBelow) {
                    matchItemsList.remove(i);
                    adapter.notifyItemRemoved(i);
                }
            }
        }

        // Kiểm tra nếu không còn card nào
        boolean hasAnyCard = false;
        for (MatchItem item : matchItemsList) {
            if (item.getType() == MatchItem.TYPE_CARD) {
                hasAnyCard = true;
                break;
            }
        }

        if (!hasAnyCard) {
            matchItemsList.clear();
            adapter.notifyDataSetChanged();
            if (rvMatchesList != null) rvMatchesList.setVisibility(View.GONE);
            if (layoutMatchesEmpty != null) layoutMatchesEmpty.setVisibility(View.VISIBLE);
        } else {
            if (rvMatchesList != null) rvMatchesList.setVisibility(View.VISIBLE);
            if (layoutMatchesEmpty != null) layoutMatchesEmpty.setVisibility(View.GONE);
        }
    }

    private void setupEmptyStateExploreButton() {
        View btnExplore = findViewById(R.id.btnEmptyExplore);
        if (btnExplore != null) {
            btnExplore.setOnClickListener(v -> {
                Intent intent = new Intent(MatchesActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.matchesBottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navDiscoveryLikes);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.navDiscoveryExplore) {
                    Intent intent = new Intent(MatchesActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryLikes) {
                    return true;
                } else if (itemId == R.id.navDiscoveryChat) {
                    Intent intent = new Intent(MatchesActivity.this, ChatListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                    return true;
                } else if (itemId == R.id.navDiscoveryProfile) {
                    Intent intent = new Intent(MatchesActivity.this, ProfileHomeActivity.class);
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
