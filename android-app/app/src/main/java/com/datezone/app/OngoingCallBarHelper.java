package com.datezone.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OngoingCallBarHelper {

    private static final String BAR_TAG = "ONGOING_CALL_BAR_TAG";
    private static final int ORIGINAL_PADDING_TAG_ID = R.id.chatDetailRoot;

    public static void updateOngoingCallBar(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (activity instanceof ChatCallActivity) return;

        ViewGroup rootContent = activity.findViewById(android.R.id.content);
        if (rootContent == null || rootContent.getChildCount() == 0) return;

        ActiveCallManager manager = ActiveCallManager.getInstance();
        boolean isCallActive = manager.isCallActive();

        View existingBar = rootContent.findViewWithTag(BAR_TAG);
        View firstChild = rootContent.getChildAt(0);

        if (!isCallActive) {
            if (existingBar != null) {
                existingBar.setVisibility(View.GONE);
            }
            if (firstChild != null) {
                Object origPaddingObj = firstChild.getTag(ORIGINAL_PADDING_TAG_ID);
                if (origPaddingObj instanceof Integer) {
                    int origTop = (Integer) origPaddingObj;
                    if (firstChild.getPaddingTop() != origTop) {
                        firstChild.setPadding(
                                firstChild.getPaddingLeft(),
                                origTop,
                                firstChild.getPaddingRight(),
                                firstChild.getPaddingBottom()
                        );
                    }
                }
            }
            return;
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int barHeightPx = (int) (42 * density);

        TextView tvTitle = null;

        if (existingBar == null) {
            LinearLayout barLayout = new LinearLayout(activity);
            barLayout.setTag(BAR_TAG);
            barLayout.setOrientation(LinearLayout.HORIZONTAL);
            barLayout.setGravity(Gravity.CENTER);
            barLayout.setBackgroundResource(R.drawable.bg_ongoing_call_bar);
            barLayout.setClickable(true);
            barLayout.setFocusable(true);
            barLayout.setPadding((int) (16 * density), 0, (int) (16 * density), 0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                barLayout.setElevation(100f * density);
            }

            ImageView ivPhone = new ImageView(activity);
            LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(
                    (int) (20 * density),
                    (int) (20 * density)
            );
            ivPhone.setLayoutParams(phoneParams);
            ivPhone.setImageResource(R.drawable.ic_call);
            ivPhone.setColorFilter(Color.WHITE);

            TextView titleView = new TextView(activity);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            titleParams.setMarginStart((int) (8 * density));
            titleView.setLayoutParams(titleParams);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            titleView.setTag("ONGOING_CALL_TITLE_TAG");

            barLayout.addView(ivPhone);
            barLayout.addView(titleView);

            FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    barHeightPx,
                    Gravity.TOP
            );
            barLayout.setLayoutParams(barParams);

            rootContent.addView(barLayout);

            existingBar = barLayout;
            tvTitle = titleView;
        } else {
            tvTitle = existingBar.findViewWithTag("ONGOING_CALL_TITLE_TAG");
        }

        // Adjust top padding of firstChild so bar does not obscure content
        if (firstChild != null && firstChild != existingBar) {
            Object origPaddingObj = firstChild.getTag(ORIGINAL_PADDING_TAG_ID);
            int origTop;
            if (origPaddingObj instanceof Integer) {
                origTop = (Integer) origPaddingObj;
            } else {
                origTop = firstChild.getPaddingTop();
                firstChild.setTag(ORIGINAL_PADDING_TAG_ID, origTop);
            }
            int targetTop = origTop + barHeightPx;
            if (firstChild.getPaddingTop() != targetTop) {
                firstChild.setPadding(
                        firstChild.getPaddingLeft(),
                        targetTop,
                        firstChild.getPaddingRight(),
                        firstChild.getPaddingBottom()
                );
            }
        }

        if (tvTitle != null) {
            tvTitle.setText("Đang gọi " + manager.getPartnerName());
        }

        existingBar.setVisibility(View.VISIBLE);
        existingBar.bringToFront();

        existingBar.setOnClickListener(v -> {
            Intent intent = new Intent(activity, ChatCallActivity.class);
            intent.putExtra(ChatCallActivity.EXTRA_NAME, manager.getPartnerName());
            intent.putExtra(ChatCallActivity.EXTRA_PHOTO_RES_ID, manager.getPartnerPhotoResId());
            intent.putExtra(ChatCallActivity.EXTRA_IS_VIDEO_CALL, manager.isVideoCall());
            if (manager.getMatchId() > 0) {
                intent.putExtra(ChatCallActivity.EXTRA_MATCH_ID, manager.getMatchId());
                intent.putExtra(ChatCallActivity.EXTRA_CALL_ID, manager.getCallId());
                intent.putExtra(ChatCallActivity.EXTRA_PHOTO_URL, manager.getPhotoUrl());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        });
    }
}
