package com.datezone.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class MatchSuccessDialog extends Dialog {

    public interface OnMatchActionListener {
        void onSayHello(String targetName);
        void onContinue();
    }

    private final String targetName;
    private final int targetPhotoResId;
    private final int userPhotoResId;
    private final OnMatchActionListener listener;
    private String remotePhotoUrl;

    public MatchSuccessDialog(@NonNull Context context, String name, String photoUrl, OnMatchActionListener listener) {
        this(context, name, R.drawable.discovery_profile_placeholder, R.drawable.discovery_profile_placeholder, listener);
        remotePhotoUrl = photoUrl;
    }

    public MatchSuccessDialog(
            @NonNull Context context,
            String targetName,
            int targetPhotoResId,
            OnMatchActionListener listener
    ) {
        this(context, targetName, targetPhotoResId, R.drawable.discovery_photo_2, listener);
    }

    public MatchSuccessDialog(
            @NonNull Context context,
            String targetName,
            int targetPhotoResId,
            int userPhotoResId,
            OnMatchActionListener listener
    ) {
        super(context, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        this.targetName = targetName != null ? targetName : "Đối tượng";
        this.targetPhotoResId = targetPhotoResId != 0 ? targetPhotoResId : R.drawable.discovery_photo_1;
        this.userPhotoResId = userPhotoResId != 0 ? userPhotoResId : R.drawable.discovery_photo_2;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.lg_dialog_match_success);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }

        setCancelable(false);

        // Thẻ 1 (Phía trước): Ảnh chính người dùng
        ImageView ivUserPhoto = findViewById(R.id.ivMatchPhotoUser);
        if (ivUserPhoto != null) {
            ivUserPhoto.setImageResource(userPhotoResId);
        }

        // Thẻ 2 (Phía sau): Ảnh chính đối tượng tương hợp
        ImageView ivTargetPhoto = findViewById(R.id.ivMatchPhotoTarget);
        if (ivTargetPhoto != null) {
            if (remotePhotoUrl != null) com.datezone.app.api.RemoteImages.load(ivTargetPhoto, remotePhotoUrl);
            else ivTargetPhoto.setImageResource(targetPhotoResId);
        }

        findViewById(R.id.btnSayHello).setOnClickListener(v -> {
            if (remotePhotoUrl == null) Toast.makeText(getContext(), "Đã gửi lời chào tới " + targetName + "!", Toast.LENGTH_SHORT).show();
            dismiss();
            if (listener != null) {
                listener.onSayHello(targetName);
            }
        });

        findViewById(R.id.btnContinueMatch).setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onContinue();
            }
        });
    }
}
