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
                        // Trang hồ sơ trả về hành động Thích / Bỏ qua -> gửi lên máy chủ
                        int index = indexOfUser(data.getLongExtra("targetId", 0));
                        if (index >= 0) {
                            swipe(matchItemsList.get(index),
                                    ProfileObject2Activity.ACTION_LIKE.equals(data.getStringExtra(ProfileObject2Activity.EXTRA_ACTION)));
                        }
                    }
                }
        );

        initMatchItemsData();
        setupRecyclerView();
        setupEmptyStateExploreButton();
        setupBottomNavigation();
    }

    private com.datezone.app.api.ApiClient api;
    private boolean swiping;

    private void initMatchItemsData() {
        matchItemsList = new ArrayList<>();
        api = new com.datezone.app.api.ApiClient(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLikes();
    }

    /** GET /home/likes - những người đã thích mình mà mình chưa quẹt, chia nhóm Hôm nay / Hôm qua / Trước đó. */
    private void loadLikes() {
        api.request(this, "GET", "home/likes?limit=100", null, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                org.json.JSONArray items = response.getJSONArray("data");
                matchItemsList.clear();
                String currentGroup = "";
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject user = items.getJSONObject(i);
                    String group = user.optString("group", "earlier");
                    if (!group.equals(currentGroup)) {
                        currentGroup = group;
                        matchItemsList.add(new MatchItem("today".equals(group) ? "HÔM NAY" : "yesterday".equals(group) ? "HÔM QUA" : "TRƯỚC ĐÓ"));
                    }
                    String name = com.datezone.app.api.ChatUi.text(user, "displayName");
                    if (!user.isNull("age")) name += ", " + user.optInt("age");
                    if (user.optBoolean("superlike")) name = "⭐ " + name;
                    matchItemsList.add(new MatchItem(user.getLong("id"), name, com.datezone.app.api.ChatUi.avatar(user), user.toString()));
                }
                adapter.notifyDataSetChanged();
                boolean empty = items.length() == 0;
                if (rvMatchesList != null) rvMatchesList.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (layoutMatchesEmpty != null) layoutMatchesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(String message) {
                Toast.makeText(MatchesActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private int indexOfUser(long userId) {
        for (int i = 0; i < matchItemsList.size(); i++) {
            MatchItem item = matchItemsList.get(i);
            if (item.getType() == MatchItem.TYPE_CARD && item.getUserId() == userId) return i;
        }
        return -1;
    }

    /** POST /home/swipe - thích lại (thành tương hợp ngay) hoặc bỏ qua 1 lượt thích. */
    private void swipe(MatchItem item, boolean like) {
        if (swiping) return;
        swiping = true;
        org.json.JSONObject body = com.datezone.app.api.ApiUi.data("targetId", item.getUserId(), "action", like ? "like" : "pass");
        api.request(this, "POST", "home/swipe", body, new com.datezone.app.api.ApiClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) throws Exception {
                swiping = false;
                org.json.JSONObject data = response.getJSONObject("data");
                removeMatchItemAt(indexOfUser(item.getUserId()));
                if (!like) {
                    Toast.makeText(MatchesActivity.this, "Đã bỏ qua Lượt thích", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!data.optBoolean("matched")) return;
                long matchId = data.optLong("matchId");
                org.json.JSONObject user = data.optJSONObject("user");
                String displayName = user == null ? item.getName() : com.datezone.app.api.ChatUi.text(user, "displayName");
                new MatchSuccessDialog(MatchesActivity.this, displayName, item.getPhotoUrl() == null ? "" : item.getPhotoUrl(),
                        new MatchSuccessDialog.OnMatchActionListener() {
                            @Override
                            public void onSayHello(String targetName) {
                                startActivity(com.datezone.app.api.ChatUi.chatIntent(MatchesActivity.this, matchId, user));
                            }

                            @Override
                            public void onContinue() {
                                // Tiếp tục xem danh sách
                            }
                        }).show();
            }
            @Override public void onError(String message) {
                swiping = false;
                Toast.makeText(MatchesActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
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
                intent.putExtra(ProfileObject2Activity.EXTRA_REMOTE_PROFILE, item.getRemoteJson());
                profileLauncher.launch(intent);
            }

            @Override
            public void onPassClick(MatchItem item, int position) {
                swipe(item, false);
            }

            @Override
            public void onLikeClick(MatchItem item, int position) {
                swipe(item, true);
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
