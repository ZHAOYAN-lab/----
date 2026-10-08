package com.beaconfinder.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.beaconfinder.app.data.SharedSync;
import com.beaconfinder.app.MainActivity;
import com.beaconfinder.app.data.AppLanguage;
import com.beaconfinder.app.sdk.WarehouseBridgeService;

/** Same map renderer and task state on phone and PC. No Javascript/native bridge. */
public final class WarehouseMapActivity extends AppCompatActivity {
    private WebView web;
    private SharedSync sync;
    private TextView status;
    private Button settings;
    private String currentLanguage;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refreshStatus = new Runnable() {
        @Override public void run() { status.setText(com.beaconfinder.app.data.AppLanguage.text(sync.status)); main.postDelayed(this, 1500); }
    };
    @Override protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(com.beaconfinder.app.data.AppLanguage.locale(base));
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); sync = SharedSync.get(this); currentLanguage = AppLanguage.locale(this).getLanguage();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 246, 250));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        LinearLayout bar = new LinearLayout(this); bar.setGravity(android.view.Gravity.CENTER_VERTICAL); bar.setPadding(dp(20), dp(12), dp(16), dp(8));
        TextView title = new TextView(this); title.setText(AppLanguage.text("物品寻找")); title.setTextSize(24); title.setTextColor(Color.rgb(20, 32, 50)); title.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Spinner language = new android.widget.Spinner(this, android.widget.Spinner.MODE_DROPDOWN);
        android.widget.ArrayAdapter<String> languages = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"中文", "日本語"});
        languages.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        language.setAdapter(languages);
        language.setSelection(com.beaconfinder.app.data.AppLanguage.locale(this).getLanguage().equals("ja") ? 1 : 0);
        language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                boolean japanese = com.beaconfinder.app.data.AppLanguage.locale(WarehouseMapActivity.this).getLanguage().equals("ja");
                if (japanese != (position == 1)) { com.beaconfinder.app.data.AppLanguage.toggle(WarehouseMapActivity.this); recreate(); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        bar.addView(language);
        root.addView(bar);
        status = new TextView(this); status.setMaxLines(1); status.setEllipsize(android.text.TextUtils.TruncateAt.END); status.setTextSize(12); status.setTextColor(Color.rgb(100, 116, 139)); status.setPadding(dp(20), 0, dp(20), dp(6)); root.addView(status);
        web = new WebView(this); web.setBackgroundColor(Color.rgb(244, 246, 250)); root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this); navigation.setPadding(dp(12), dp(4), dp(12), dp(4)); navigation.setBackgroundColor(Color.WHITE);
        Button home = navigationButton("寻找", true); home.setOnClickListener(v -> { if (sync.configured()) load(); else configure(); });
        Button devices = navigationButton("设备控制", false); devices.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        settings = navigationButton("设置", false); settings.setOnClickListener(v -> showSettings());
        navigation.addView(home, new LinearLayout.LayoutParams(0, dp(52), 1)); navigation.addView(devices, new LinearLayout.LayoutParams(0, dp(52), 1)); navigation.addView(settings, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(navigation); setContentView(root);
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl(), server = Uri.parse(sync.address());
                return !server.getScheme().equals(target.getScheme()) || !server.getAuthority().equals(target.getAuthority());
            }
        });
        if (sync.configured()) load(); else showWelcome();
        main.post(refreshStatus);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private Button navigationButton(String label, boolean selected) {
        Button button = new Button(this); button.setText(AppLanguage.text(label)); button.setTextSize(14); button.setAllCaps(false); button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(0, 0, 0, 0); button.setBackgroundColor(Color.TRANSPARENT);
        button.setTextColor(selected ? Color.rgb(37, 99, 235) : Color.rgb(100, 116, 139)); return button;
    }
    private void showSettings() {
        new AlertDialog.Builder(this).setTitle(AppLanguage.text("设置"))
                .setItems(new String[]{AppLanguage.text("同步设置"), AppLanguage.text("冲突处理")}, (d, which) -> { if (which == 0) configure(); else conflict(); })
                .setNegativeButton(AppLanguage.text("取消"), null).show();
    }
    private void showWelcome() {
        String title = AppLanguage.text("物品寻找"), description = AppLanguage.text("配置同步服务后，仓库地图与商品会自动显示。"), action = AppLanguage.text("打开下方设置，完成同步配置");
        web.loadData("<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0;background:#f4f6fa;font-family:sans-serif;color:#142032'><div style='margin:32px 20px;padding:40px 24px;background:white;border-radius:24px;text-align:center'><div style='font-size:48px;color:#2563eb'>⌖</div><h2>" + title + "</h2><p style='color:#64748b;line-height:1.8'>" + description + "</p><p style='color:#2563eb;font-size:14px'>" + action + "</p></div></body></html>", "text/html", "UTF-8");
    }
    @Override protected void onResume() {
        super.onResume();
        if (currentLanguage != null && !currentLanguage.equals(AppLanguage.locale(this).getLanguage())) recreate();
        else if (sync != null && sync.configured()) { WarehouseBridgeService.start(this); sync.sync(null); }
    }
    private void load() {
        try {
            WarehouseBridgeService.start(this);
            sync.sync(null);
            web.loadUrl(sync.address() + "/console/?mobile=1&lang=" + com.beaconfinder.app.data.AppLanguage.locale(this).getLanguage() + "#token=" + Uri.encode(sync.token()));
        } catch (Exception e) { status.setText(com.beaconfinder.app.data.AppLanguage.text("连接失败：") + e.getMessage()); }
    }
    private void configure() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(32, 16, 32, 16);
        TextView info = new TextView(this); info.setText(com.beaconfinder.app.data.AppLanguage.text("填写共享服务地址与连接密钥，完成后自动同步仓库、商品和地图。")); form.addView(info);
        EditText address = new EditText(this); address.setSingleLine(true); address.setHint("http://192.168.1.20:8088"); address.setText(sync.address()); address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); form.addView(address);
        EditText token = new EditText(this); token.setSingleLine(true); token.setHint(com.beaconfinder.app.data.AppLanguage.text("连接密钥")); token.setText(sync.token()); token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); form.addView(token);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(com.beaconfinder.app.data.AppLanguage.text("同步设置")).setView(form).setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("取消"), null).setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("连接"), null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { sync.configure(address.getText().toString(), token.getText().toString()); dialog.dismiss(); load(); }
            catch (Exception e) { address.setError(e.getMessage()); }
        })); dialog.show();
    }
    private void conflict() {
        if (!sync.hasConflict()) { new AlertDialog.Builder(this).setMessage(com.beaconfinder.app.data.AppLanguage.text("当前没有资料冲突")).setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("确定"), null).show(); return; }
        new AlertDialog.Builder(this).setTitle(com.beaconfinder.app.data.AppLanguage.text("资料同时被修改"))
                .setMessage(com.beaconfinder.app.data.AppLanguage.text("本地修改已保留。选择保留手机修改并重新同步，或采用 PC 资料。处理前会保存本地备份。"))
                .setPositiveButton(com.beaconfinder.app.data.AppLanguage.text("保留手机修改"), (d, w) -> resolve(true))
                .setNeutralButton(com.beaconfinder.app.data.AppLanguage.text("采用 PC 资料"), (d, w) -> resolve(false)).setNegativeButton(com.beaconfinder.app.data.AppLanguage.text("稍后"), null).show();
    }
    private void resolve(boolean keepLocal) { try { sync.resolveConflict(keepLocal); } catch (Exception e) { status.setText(e.getMessage()); } }
    @Override public void onDestroy() { main.removeCallbacksAndMessages(null); web.destroy(); super.onDestroy(); }
}
