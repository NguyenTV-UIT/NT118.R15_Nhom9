package com.datezone.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.Locale;

public class LocationDistanceActivity extends AppCompatActivity {

    private Double latitude, longitude;
    private TextView tvLocation;
    private TextView tvDistance;
    private SeekBar seekDistance;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.lg6_activity_location_distance);

        View root = findViewById(R.id.locationDistanceRoot);
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

        tvLocation = findViewById(R.id.tvLocation);
        tvDistance = findViewById(R.id.tvDistance);
        seekDistance = findViewById(R.id.seekDistance);

        // Đăng ký Launcher yêu cầu quyền truy cập Vị trí
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (Boolean.TRUE.equals(fineGranted) || Boolean.TRUE.equals(coarseGranted)) {
                        fetchCurrentGpsLocation();
                    } else {
                        Toast.makeText(this, "Quyền vị trí bị từ chối. Bạn có thể chọn khu vực thủ công.", Toast.LENGTH_SHORT).show();
                        showManualLocationDialog();
                    }
                }
        );

        // Xử lý thanh trượt khoảng cách (1 - 100 km)
        seekDistance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int distanceKm = progress + 1; // 0..99 tương ứng 1..100 km
                tvDistance.setText(String.valueOf(distanceKm));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Click vào khung vị trí hoặc nút Đổi -> Xin quyền vị trí hoặc mở hộp thoại đổi khu vực
        View.OnClickListener locationClickListener = v -> handleLocationClick();
        findViewById(R.id.layoutLocationContainer).setOnClickListener(locationClickListener);
        findViewById(R.id.btnChangeLocation).setOnClickListener(locationClickListener);

        // Nút Quay lại góc trên bên trái -> quay về trang trước
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Nút Tiếp tục dùng chung
        findViewById(R.id.btnContinue).setOnClickListener(v -> checkAndContinue());
    }

    private void handleLocationClick() {
        String[] options = {"Tự động phát hiện vị trí hiện tại (GPS)", "Nhập / Chọn khu vực thủ công"};
        new AlertDialog.Builder(this)
                .setTitle("Chọn vị trí của bạn")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkPermissionAndFetchLocation();
                    } else {
                        showManualLocationDialog();
                    }
                })
                .show();
    }

    private void checkPermissionAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentGpsLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressWarnings("MissingPermission")
    private void fetchCurrentGpsLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showManualLocationDialog();
            return;
        }

        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (!isGpsEnabled && !isNetworkEnabled) {
                    Toast.makeText(this, "Vui lòng bật GPS trên thiết bị để lấy vị trí tự động", Toast.LENGTH_SHORT).show();
                    showManualLocationDialog();
                    return;
                }

                Location lastLocation = null;
                if (isNetworkEnabled) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                if (lastLocation == null && isGpsEnabled) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }

                if (lastLocation != null) {
                    latitude = lastLocation.getLatitude();
                    longitude = lastLocation.getLongitude();
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(lastLocation.getLatitude(), lastLocation.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String cityName = address.getAdminArea(); // Tỉnh/Thành phố
                        if (cityName == null || cityName.isEmpty()) {
                            cityName = address.getLocality();
                        }
                        if (cityName == null || cityName.isEmpty()) {
                            cityName = address.getFeatureName();
                        }

                        if (cityName != null && !cityName.isEmpty()) {
                            tvLocation.setText(cityName);
                            Toast.makeText(this, "Đã cập nhật vị trí: " + cityName, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore geocoding exception
        }

        Toast.makeText(this, "Chưa lấy được tên khu vực. Vui lòng chọn khu vực của bạn.", Toast.LENGTH_SHORT).show();
        showManualLocationDialog();
    }

    private void showManualLocationDialog() {
        final String[] popularCities = {
                "Thành phố Hồ Chí Minh",
                "Hà Nội",
                "Đà Nẵng",
                "Cần Thơ",
                "Hải Phòng",
                "Nha Trang (Khánh Hòa)",
                "Đà Lạt (Lâm Đồng)",
                "Vũng Tàu (Bà Rịa - Vũng Tàu)",
                "Bình Dương"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn khu vực của bạn");

        builder.setSingleChoiceItems(popularCities, -1, (dialog, which) -> {
            latitude = null; longitude = null;
            tvLocation.setText(popularCities[which]);
            Toast.makeText(this, "Đã chọn: " + popularCities[which], Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        builder.setNegativeButton("Nhập tên khác", (dialog, which) -> showCustomCityInputDialog());
        builder.show();
    }

    private void showCustomCityInputDialog() {
        final EditText input = new EditText(this);
        input.setHint("Nhập tên Thành phố / Tỉnh...");

        new AlertDialog.Builder(this)
                .setTitle("Nhập khu vực của bạn")
                .setView(input)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    String customCity = input.getText().toString().trim();
                    if (!customCity.isEmpty()) {
                        latitude = null; longitude = null;
                        tvLocation.setText(customCity);
                        Toast.makeText(this, "Đã cập nhật khu vực: " + customCity, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void checkAndContinue() {
        String location = tvLocation.getText().toString().trim();
        int distanceKm = seekDistance.getProgress() + 1;

        // Chuyển sang lg7_activity_height
        android.content.Intent intent = new android.content.Intent(LocationDistanceActivity.this, HeightActivity.class);
        intent.putExtra("EXTRA_LOCATION", location);
        intent.putExtra("EXTRA_DISTANCE_KM", distanceKm);
        com.datezone.app.api.ApiUi.send(this, R.id.btnContinue, true, "PUT", "users/me/profile",
                com.datezone.app.api.ApiUi.data("city", location, "latitude", latitude, "longitude", longitude), response ->
                    com.datezone.app.api.ApiUi.save(this, "users/me/preferences",
                            com.datezone.app.api.ApiUi.data("maxDistanceKm", distanceKm), intent));
    }
}
