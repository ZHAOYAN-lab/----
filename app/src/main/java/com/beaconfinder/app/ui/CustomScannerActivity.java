package com.beaconfinder.app.ui;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.beaconfinder.app.R;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

/**
 * 縦画面固定・退出ボタン・フラッシュライト制御を備えたカスタムスキャナ画面
 */
public class CustomScannerActivity extends CaptureActivity implements DecoratedBarcodeView.TorchListener {

    private DecoratedBarcodeView barcodeScannerView;
    private ImageView btnFlash;
    private boolean isTorchOn = false;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        java.util.Locale locale = java.util.Locale.JAPAN;
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected DecoratedBarcodeView initializeContent() {
        setContentView(R.layout.activity_custom_scanner);
        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner);
        return barcodeScannerView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 戻るボタン
        View btnBack = findViewById(R.id.btn_scanner_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // キャンセルボタン
        View btnCancel = findViewById(R.id.btn_scanner_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish());
        }

        // フラッシュライトボタン
        btnFlash = findViewById(R.id.btn_scanner_flash);
        if (btnFlash != null) {
            if (!hasFlash()) {
                btnFlash.setVisibility(View.GONE);
            } else {
                barcodeScannerView.setTorchListener(this);
                btnFlash.setOnClickListener(v -> {
                    if (isTorchOn) {
                        barcodeScannerView.setTorchOff();
                    } else {
                        barcodeScannerView.setTorchOn();
                    }
                });
            }
        }

        // タイトルおよびプロンプト
        TextView tvTitle = findViewById(R.id.tv_scanner_title);
        TextView tvHint = findViewById(R.id.tv_scanner_hint);

        String customTitle = getIntent().getStringExtra("SCAN_TITLE");
        if (customTitle != null && tvTitle != null) {
            tvTitle.setText(customTitle);
        }

        String prompt = getIntent().getStringExtra(Intents.Scan.PROMPT_MESSAGE);
        if (prompt != null && !prompt.trim().isEmpty() && tvHint != null) {
            tvHint.setText(prompt);
        }

        // カメラ設定: コンティニュアスフォーカスを有効化して高速デコード
        try {
            barcodeScannerView.getBarcodeView().getCameraSettings().setContinuousFocusEnabled(true);
        } catch (Exception ignored) {}
    }

    private boolean hasFlash() {
        return getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    @Override
    public void onTorchOn() {
        isTorchOn = true;
        if (btnFlash != null) {
            btnFlash.setImageResource(R.drawable.ic_flash_on_white);
        }
    }

    @Override
    public void onTorchOff() {
        isTorchOn = false;
        if (btnFlash != null) {
            btnFlash.setImageResource(R.drawable.ic_flash_off_white);
        }
    }
}
