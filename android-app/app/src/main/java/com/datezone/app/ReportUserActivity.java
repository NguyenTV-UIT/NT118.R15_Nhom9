package com.datezone.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ReportUserActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "EXTRA_NAME";
    /** id người bị báo cáo trên máy chủ; có thì gửi POST /users/:id/report. */
    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";
    private long targetUserId;

    private String targetUserName = "Trương Vĩnh Nguyên";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.discovery_activity_report_user);

        View root = findViewById(R.id.reportUserRoot);
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

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_NAME)) {
            String name = intent.getStringExtra(EXTRA_NAME);
            if (name != null && !name.isEmpty()) {
                targetUserName = name;
            }
        }

        // Nút Back góc trên bên trái -> tái sử dụng nút back chuẩn
        findViewById(R.id.btnReportBack).setOnClickListener(v -> finish());

        // Nút Báo cáo và chặn
        targetUserId = intent != null ? intent.getLongExtra(EXTRA_USER_ID, 0) : 0;
        findViewById(R.id.btnSubmitReport).setOnClickListener(v -> {
            if (targetUserId > 0) sendReport();
            else submitReportAndBlock();
        });
    }

    /** POST /users/:id/report { reason, details } - máy chủ lưu báo cáo, chặn và huỷ tương hợp. */
    private void sendReport() {
        android.widget.RadioGroup group = findViewById(R.id.rgReportReasons);
        int checked = group == null ? -1 : group.getCheckedRadioButtonId();
        String reason = checked == R.id.rbReasonFakePhoto ? "fake_profile"
                : checked == R.id.rbReasonSensitiveContent ? "sexual_content"
                : checked == R.id.rbReasonHarassment ? "harassment"
                : checked == R.id.rbReasonSpam ? "scam_spam"
                : checked == R.id.rbReasonUnderage ? "underage"
                : checked == R.id.rbReasonOther ? "other" : null;
        if (reason == null) {
            Toast.makeText(this, "Vui lòng chọn lý do báo cáo", Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.EditText edtDetails = findViewById(R.id.edtReportDescription);
        String details = edtDetails == null ? "" : edtDetails.getText().toString().trim();
        org.json.JSONObject body = com.datezone.app.api.ApiUi.data("reason", reason);
        try { if (!details.isEmpty()) body.put("details", details); }
        catch (org.json.JSONException ignored) { }
        com.datezone.app.api.ApiUi.send(this, R.id.btnSubmitReport, true, "POST", "users/" + targetUserId + "/report", body,
                response -> submitReportAndBlock());
    }

    private void submitReportAndBlock() {
        ReportSuccessDialog dialog = new ReportSuccessDialog(
                this,
                targetUserName,
                () -> {
                    Toast.makeText(this, "Đã gửi báo cáo và chặn thành công!", Toast.LENGTH_SHORT).show();

                    // Chuyển về màn hình chính HomeActivity và xóa sạch stack
                    Intent intent = new Intent(ReportUserActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
        );
        dialog.show();
    }
}
