package com.datezone.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

public class PhotoViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PHOTOS = "EXTRA_PHOTOS";
    public static final String EXTRA_PHOTO_URLS = "photoUrls";
    private String[] photoUrls;

    private int photoCount() { return photoUrls == null ? photoResIds.length : photoUrls.length; }

    private void showPhoto(ImageView view, int index) {
        if (photoUrls == null) view.setImageResource(photoResIds[index]);
        else com.datezone.app.api.RemoteImages.load(view, photoUrls[index]);
    }

    private ImageView ivMainPhoto;
    private LinearLayout layoutThumbnails;

    private int[] photoResIds = new int[]{
            R.drawable.discovery_photo_1,
            R.drawable.discovery_photo_2,
            R.drawable.discovery_photo_3,
            R.drawable.discovery_photo_4,
            R.drawable.discovery_photo_5
    };

    private int currentPhotoIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.discovery_activity_photo_viewer);

        View root = findViewById(R.id.photoViewerRoot);
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
        if (intent != null && intent.hasExtra(EXTRA_PHOTO_URLS)) {
            photoUrls = intent.getStringArrayExtra(EXTRA_PHOTO_URLS);
            if (photoUrls == null) photoUrls = new String[0];
        }
        if (intent != null && intent.hasExtra(EXTRA_PHOTOS)) {
            int[] customPhotos = intent.getIntArrayExtra(EXTRA_PHOTOS);
            if (customPhotos != null && customPhotos.length > 0) {
                photoResIds = customPhotos;
            }
        }

        ivMainPhoto = findViewById(R.id.ivPhotoViewerMain);
        layoutThumbnails = findViewById(R.id.photoViewerThumbnails);

        // Nút Back
        findViewById(R.id.btnPhotoViewerBack).setOnClickListener(v -> finish());

        setupSwipeGesture();
        renderMainPhotoAndThumbnails();
    }

    private void setupSwipeGesture() {
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 80;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Quẹt sang phải -> Xem ảnh trước
                            if (currentPhotoIndex > 0) {
                                currentPhotoIndex--;
                                renderMainPhotoAndThumbnails();
                            }
                        } else {
                            // Quẹt sang trái -> Xem ảnh tiếp theo
                            if (currentPhotoIndex < photoCount() - 1) {
                                currentPhotoIndex++;
                                renderMainPhotoAndThumbnails();
                            }
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (ivMainPhoto != null) {
                    float viewWidth = ivMainPhoto.getWidth();
                    if (viewWidth > 0) {
                        if (e.getX() < viewWidth / 3f) {
                            if (currentPhotoIndex > 0) {
                                currentPhotoIndex--;
                                renderMainPhotoAndThumbnails();
                            }
                        } else if (e.getX() > (viewWidth * 2f / 3f)) {
                            if (currentPhotoIndex < photoCount() - 1) {
                                currentPhotoIndex++;
                                renderMainPhotoAndThumbnails();
                            }
                        }
                    }
                }
                return super.onSingleTapConfirmed(e);
            }
        });

        View container = findViewById(R.id.photoViewerImageContainer);
        if (container != null) {
            container.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }
    }

    private void renderMainPhotoAndThumbnails() {
        if (photoCount() == 0) {
            ivMainPhoto.setImageResource(R.drawable.discovery_profile_placeholder);
            return;
        }

        if (currentPhotoIndex < 0) currentPhotoIndex = 0;
        if (currentPhotoIndex >= photoCount()) currentPhotoIndex = photoCount() - 1;

        // Hiển thị ảnh chính
        showPhoto(ivMainPhoto, currentPhotoIndex);

        // Cập nhật danh sách thumbnail
        if (layoutThumbnails != null) {
            layoutThumbnails.removeAllViews();
            float densityDp = getResources().getDisplayMetrics().density;
            int cardSizePx = (int) (56 * densityDp);
            int marginEndPx = (int) (10 * densityDp);

            int activeBorderColor = ContextCompat.getColor(this, R.color.brand_primary);
            int inactiveBorderColor = ContextCompat.getColor(this, R.color.border_input);

            for (int i = 0; i < photoCount(); i++) {
                final int index = i;

                MaterialCardView cardView = new MaterialCardView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardSizePx, cardSizePx);
                params.setMarginEnd(marginEndPx);
                cardView.setLayoutParams(params);
                cardView.setRadius(10 * densityDp);
                cardView.setCardElevation(0);

                if (i == currentPhotoIndex) {
                    cardView.setStrokeColor(activeBorderColor);
                    cardView.setStrokeWidth((int) (2 * densityDp));
                    cardView.setAlpha(1.0f);
                } else {
                    cardView.setStrokeColor(inactiveBorderColor);
                    cardView.setStrokeWidth(0);
                    cardView.setAlpha(0.5f);
                }

                ImageView ivThumb = new ImageView(this);
                ivThumb.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                ));
                ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                showPhoto(ivThumb, i);

                cardView.addView(ivThumb);
                cardView.setOnClickListener(v -> {
                    currentPhotoIndex = index;
                    renderMainPhotoAndThumbnails();
                });

                layoutThumbnails.addView(cardView);
            }
        }
    }
}
