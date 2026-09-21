package com.usb.smartcontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class SettingsActivity extends Activity {
    private static final int MODE_ORIENTATION_ID = 1001;
    private static final int MODE_FOREGROUND_ID = 1002;
    private static final int MODE_DISABLED_ID = 1003;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<GameApp> installedApps = new ArrayList<>();
    private final Set<String> selectedPackages = new TreeSet<>();

    private TextView activationStatus;
    private LinearLayout activationCard;
    private TextView gameSummary;
    private Switch autoAdb;
    private Switch autoMtp;
    private Switch mtpUnlockOnly;
    private Switch launcherVisible;
    private RadioGroup gameMode;
    private Button chooseGames;
    private Button save;
    private SharedPreferences preferences;
    private XposedService xposedService;
    private int detectedGameCount;
    private boolean gameScanComplete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("USB 智能控制");
        buildUi();
        bindXposedService();
        scanInstalledApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInjectionStatus();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(32));
        content.setBackgroundColor(color(R.color.page_background));

        TextView title = new TextView(this);
        title.setText("USB 智能控制");
        title.setTextColor(color(R.color.text_primary));
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("快速、自动且可配置的 USB 状态控制");
        subtitle.setTextColor(color(R.color.text_secondary));
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        content.addView(subtitle, matchWrap());

        activationCard = card();
        activationStatus = bodyText("● 未激活");
        activationStatus.setLineSpacing(dp(4), 1f);
        activationCard.addView(activationStatus, matchWrap());
        activationCard.setOnClickListener(ignored -> refreshInjectionStatus());
        content.addView(activationCard, cardParams());

        LinearLayout appCard = card();
        addSectionTitle(appCard, "应用");
        launcherVisible = addSwitch(appCard, "在桌面显示图标", "隐藏后可从 LSPosed 模块列表重新打开");
        launcherVisible.setChecked(isLauncherVisible());
        launcherVisible.setOnCheckedChangeListener((button, visible) -> setLauncherVisible(visible));
        content.addView(appCard, cardParams());

        LinearLayout usbCard = card();
        addSectionTitle(usbCard, "USB 自动控制");
        autoAdb = addSwitch(usbCard, "自动控制 USB 调试", "接入后快速开启，拔出后关闭");
        autoMtp = addSwitch(usbCard, "自动启用文件传输", "连接 USB 时切换到 MTP");
        mtpUnlockOnly = addSwitch(usbCard, "仅在解锁时启用 MTP", "锁屏时保护设备中的文件");
        content.addView(usbCard, cardParams());

        LinearLayout gameCard = card();
        addSectionTitle(gameCard, "游戏场景");
        TextView explanation = bodyText("选择识别方式，游戏期间会临时关闭 USB 调试。");
        explanation.setPadding(0, 0, 0, dp(8));
        gameCard.addView(explanation, matchWrap());

        gameMode = new RadioGroup(this);
        gameMode.setOrientation(RadioGroup.VERTICAL);
        addRadio(gameMode, MODE_ORIENTATION_ID, "按横竖屏判断");
        addRadio(gameMode, MODE_FOREGROUND_ID, "按前台游戏应用判断");
        addRadio(gameMode, MODE_DISABLED_ID, "关闭游戏场景控制");
        gameCard.addView(gameMode, matchWrap());

        gameSummary = bodyText("正在识别手机中的游戏应用…");
        gameSummary.setPadding(0, dp(12), 0, dp(8));
        gameCard.addView(gameSummary, matchWrap());

        chooseGames = secondaryButton("选择游戏应用");
        chooseGames.setEnabled(false);
        chooseGames.setOnClickListener(ignored -> showGamePicker());
        gameCard.addView(chooseGames, matchWrap());
        content.addView(gameCard, cardParams());

        save = primaryButton("保存并立即应用");
        save.setEnabled(false);
        save.setOnClickListener(ignored -> savePreferences());
        LinearLayout.LayoutParams saveParams = matchWrap();
        saveParams.topMargin = dp(6);
        content.addView(save, saveParams);

        TextView note = bodyText("配置保存后会立即同步；首次启用或更新模块后，需重启设备让当前版本注入系统框架。");
        note.setPadding(dp(4), dp(14), dp(4), 0);
        content.addView(note, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        setContentView(scrollView);
    }

    private boolean isLauncherVisible() {
        int state = getPackageManager().getComponentEnabledSetting(launcherComponent());
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void setLauncherVisible(boolean visible) {
        getPackageManager().setComponentEnabledSetting(
                launcherComponent(),
                visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private ComponentName launcherComponent() {
        return new ComponentName(this, getPackageName() + ".Launcher");
    }

    private void bindXposedService() {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService service) {
                runOnUiThread(() -> {
                    xposedService = service;
                    try {
                        preferences = service.getRemotePreferences(Config.GROUP);
                        loadPreferences();
                        save.setEnabled(true);
                        refreshInjectionStatus();
                    } catch (Throwable error) {
                        setActivation(false, "配置读取失败：" + safeMessage(error),
                                "检查框架版本和模块日志后重试。");
                    }
                });
            }

            @Override
            public void onServiceDied(XposedService service) {
                runOnUiThread(() -> {
                    xposedService = null;
                    preferences = null;
                    setActivation(false, "未检测到模块框架服务。",
                            "请在模块管理器中启用模块后重启设备。");
                    save.setEnabled(false);
                });
            }
        });
    }

    private void refreshInjectionStatus() {
        XposedService service = xposedService;
        if (service == null) {
            setActivation(false, "未检测到模块框架服务。",
                    "请确认模块已启用，并返回本页面重新检查。");
            return;
        }
        worker.execute(() -> {
            boolean active = false;
            String reason = "尚未发现 system_server 注入记录。";
            String advice = "请确认静态作用域为 system，然后重启设备。";
            try {
                HookedTarget system = null;
                for (HookedTarget target : service.getRunningTargets()) {
                    String process = target.getProcessName();
                    if ("system_server".equals(process) || "system".equals(process)) {
                        system = target;
                        break;
                    }
                }
                if (system != null) {
                    boolean currentVersion = system.getLoadedVersionCode() == BuildConfig.VERSION_CODE;
                    boolean upToDate = system.getState() == HookedTarget.State.UP_TO_DATE;
                    active = currentVersion && upToDate;
                    if (!active) {
                        reason = "系统框架仍在运行旧版本。";
                        advice = "请重启设备，让当前版本重新注入系统框架。";
                    }
                }
            } catch (Throwable error) {
                reason = "注入状态读取失败：" + safeMessage(error);
                advice = "请确认框架支持 API 102，并检查模块日志。";
            }
            boolean finalActive = active;
            String finalReason = reason;
            String finalAdvice = advice;
            runOnUiThread(() -> setActivation(finalActive, finalReason, finalAdvice));
        });
    }

    private void scanInstalledApps() {
        worker.execute(() -> {
            PackageManager packageManager = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = packageManager.queryIntentActivities(launcher, 0);
            Map<String, GameApp> unique = new HashMap<>();
            for (ResolveInfo info : resolved) {
                if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                    continue;
                }
                ApplicationInfo app = info.activityInfo.applicationInfo;
                if (getPackageName().equals(app.packageName)) {
                    continue;
                }
                boolean game = app.category == ApplicationInfo.CATEGORY_GAME
                        || (app.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
                if (!game) {
                    continue;
                }
                String label = String.valueOf(info.loadLabel(packageManager));
                unique.put(app.packageName, new GameApp(label, app.packageName));
            }

            List<GameApp> apps = new ArrayList<>(unique.values());
            Collator collator = Collator.getInstance(Locale.getDefault());
            apps.sort((left, right) -> collator.compare(left.label, right.label));
            int finalGameCount = apps.size();
            runOnUiThread(() -> {
                installedApps.clear();
                installedApps.addAll(apps);
                detectedGameCount = finalGameCount;
                gameScanComplete = true;
                chooseGames.setEnabled(!installedApps.isEmpty());
                updateGameSummary();
            });
        });
    }

    private void showGamePicker() {
        if (installedApps.isEmpty()) {
            Toast.makeText(this, "没有识别到系统标记的游戏应用", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] labels = new CharSequence[installedApps.size()];
        boolean[] checked = new boolean[installedApps.size()];
        Set<String> pending = new HashSet<>(selectedPackages);
        for (int i = 0; i < installedApps.size(); i++) {
            GameApp app = installedApps.get(i);
            labels[i] = app.label + "\n" + app.packageName;
            checked[i] = pending.contains(app.packageName);
        }

        new AlertDialog.Builder(this)
                .setTitle("选择游戏应用 · " + detectedGameCount + " 个")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    String packageName = installedApps.get(which).packageName;
                    if (isChecked) {
                        pending.add(packageName);
                    } else {
                        pending.remove(packageName);
                    }
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("完成", (dialog, which) -> {
                    selectedPackages.clear();
                    selectedPackages.addAll(pending);
                    updateGameSummary();
                })
                .show();
    }

    private void loadPreferences() {
        autoAdb.setChecked(Config.autoAdb(preferences));
        autoMtp.setChecked(Config.autoMtp(preferences));
        mtpUnlockOnly.setChecked(Config.mtpUnlockOnly(preferences));
        String mode = Config.gameMode(preferences);
        if (Config.MODE_FOREGROUND.equals(mode)) {
            gameMode.check(MODE_FOREGROUND_ID);
        } else if (Config.MODE_DISABLED.equals(mode)) {
            gameMode.check(MODE_DISABLED_ID);
        } else {
            gameMode.check(MODE_ORIENTATION_ID);
        }
        selectedPackages.clear();
        selectedPackages.addAll(Config.gamePackages(preferences));
        updateGameSummary();
    }

    private void savePreferences() {
        if (preferences == null) {
            Toast.makeText(this, "请先在模块管理器中启用模块", Toast.LENGTH_LONG).show();
            return;
        }
        String mode;
        if (gameMode.getCheckedRadioButtonId() == MODE_FOREGROUND_ID) {
            mode = Config.MODE_FOREGROUND;
        } else if (gameMode.getCheckedRadioButtonId() == MODE_DISABLED_ID) {
            mode = Config.MODE_DISABLED;
        } else {
            mode = Config.MODE_ORIENTATION;
        }
        preferences.edit()
                .putBoolean(Config.AUTO_ADB, autoAdb.isChecked())
                .putBoolean(Config.AUTO_MTP, autoMtp.isChecked())
                .putBoolean(Config.MTP_UNLOCK_ONLY, mtpUnlockOnly.isChecked())
                .putString(Config.GAME_MODE, mode)
                .putStringSet(Config.GAME_PACKAGES, new TreeSet<>(selectedPackages))
                .apply();
        Toast.makeText(this, "配置已保存并立即应用", Toast.LENGTH_SHORT).show();
    }

    private void updateGameSummary() {
        if (!gameScanComplete) {
            gameSummary.setText("正在识别手机中的游戏应用…");
        } else if (installedApps.isEmpty()) {
            gameSummary.setText("未识别到系统标记的游戏应用");
        } else {
            gameSummary.setText("识别到 " + detectedGameCount + " 个游戏 · 已选择 "
                    + selectedPackages.size() + " 个应用");
        }
    }

    private void setActivation(boolean active, String reason, String advice) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(18));
        if (active) {
            background.setColor(color(R.color.status_active_background));
            background.setStroke(dp(1), color(R.color.status_active_border));
            activationStatus.setText("● 已激活");
            activationStatus.setTextColor(color(R.color.status_active_text));
        } else {
            background.setColor(color(R.color.status_inactive_background));
            background.setStroke(dp(1), color(R.color.status_inactive_border));
            activationStatus.setText("● 未激活\n\n原因：" + reason + "\n建议：" + advice);
            activationStatus.setTextColor(color(R.color.status_inactive_text));
        }
        activationCard.setBackground(background);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color(R.color.card_background));
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), color(R.color.card_border));
        card.setBackground(background);
        card.setElevation(dp(2));
        return card;
    }

    private void addSectionTitle(LinearLayout parent, String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(color(R.color.text_primary));
        view.setTextSize(19);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(10));
        parent.addView(view, matchWrap());
    }

    private Switch addSwitch(LinearLayout parent, String title, String description) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(color(R.color.text_primary));
        titleView.setTextSize(16);
        labels.addView(titleView, matchWrap());
        TextView descriptionView = bodyText(description);
        descriptionView.setTextSize(13);
        labels.addView(descriptionView, matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setButtonTintList(ColorStateList.valueOf(color(R.color.accent)));
        row.addView(toggle, wrapWrap());
        parent.addView(row, matchWrap());
        return toggle;
    }

    private void addRadio(RadioGroup group, int id, String label) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        button.setTextColor(color(R.color.text_primary));
        button.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color(R.color.accent), color(R.color.text_secondary)}));
        group.addView(button, matchWrap());
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color(R.color.text_secondary));
        view.setTextSize(14);
        return view;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.accent)));
        button.setMinHeight(dp(52));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(color(R.color.accent));
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.secondary_button)));
        return button;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(14);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int color(int resource) {
        return getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private record GameApp(String label, String packageName) {
    }
}
