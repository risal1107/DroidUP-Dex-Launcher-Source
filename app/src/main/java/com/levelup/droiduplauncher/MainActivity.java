package com.levelup.droiduplauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    public static final long LAUNCH_DEBOUNCE_MS = 600L;
    public static final int MODE_COMPACT = 0;
    public static final int MODE_MAXIMIZED = 1;
    public static final int MODE_NORMAL = 2;

    public static final String PREFS_NAME = "droidup_launcher_prefs";
    public static final String PREF_DESKTOP_SHORTCUTS = "desktop_shortcuts";
    public static final String PREF_DESKTOP_SHORTCUTS_ORDERED = "desktop_shortcuts_ordered";

    // Colors matching Samsung DeX aesthetic
    public final int TASKBAR = Color.argb(0xEA, 0x0A, 0x0C, 0x10); // Translucent Dark Acrylic
    public final int PANEL = Color.argb(0xF0, 0x14, 0x17, 0x1F);   // Start menu background
    public final int PANEL_2 = Color.argb(0x40, 0xFF, 0xFF, 0xFF); // Subtle button hover
    public final int PANEL_3 = Color.argb(0x20, 0xFF, 0xFF, 0xFF); // Minimal border/pill
    public final int ACCENT = Color.argb(0xFF, 0x3D, 0x7E, 0xFF);  // Samsung DeX Blue Accent
    public final int MUTED = Color.argb(0xFF, 0x90, 0x9E, 0xB4);   // Gray text

    private PackageManager packageManager;
    private final List<AppEntry> apps = new ArrayList<>();
    private final List<AppEntry> desktopShortcuts = new ArrayList<>();
    private final List<AppEntry> recentApps = new ArrayList<>();

    private int currentDisplayId = 0;
    private long lastLaunchElapsed = 0L;

    private FrameLayout root;
    private FrameLayout desktopArea;
    private LinearLayout desktopShortcutContainer;
    private LinearLayout recentTaskbarStrip;
    private FrameLayout startButton;
    private FrameLayout startMenuLayer;
    private GridView startMenuGrid;
    private boolean startMenuVisible = false;

    private FrameLayout appControlLayer;
    private AppEntry activeApp;

    // Status items
    private TextView batteryPercentTextView;
    private BroadcastReceiver batteryReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterDesktopFullscreen();
        packageManager = getPackageManager();

        Display display = getDisplay();
        if (display != null) {
            currentDisplayId = display.getDisplayId();
        }

        loadApps();
        loadDesktopShortcuts();
        buildDesktopUi();
        refreshDesktopShortcuts();
        refreshRecentTaskbar();
        registerBatteryReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterDesktopFullscreen();
        refreshDesktopShortcuts();
        refreshRecentTaskbar();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        enterDesktopFullscreen();
        hideStartMenu();
        hideAppControls();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterDesktopFullscreen();
        }
    }

    @Override
    public void onBackPressed() {
        if (appControlLayer != null && appControlLayer.getVisibility() == View.VISIBLE) {
            hideAppControls();
            return;
        }
        if (startMenuVisible) {
            hideStartMenu();
            return;
        }
        // Mimic back button behavior
        bringLauncherToFront();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {
            }
        }
    }

    private void enterDesktopFullscreen() {
        Window window = getWindow();
        window.setFlags(0x00000400, 0x00000400); // FLAG_FULLSCREEN
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    public int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public GradientDrawable roundedDrawable(int color, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp((int) radiusDp));
        return gd;
    }

    private void loadApps() {
        apps.clear();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
        String selfPkg = getPackageName();

        for (ResolveInfo info : resolveInfos) {
            ActivityInfo aInfo = info.activityInfo;
            if (aInfo == null || selfPkg.equals(aInfo.packageName)) {
                continue;
            }
            String label = info.loadLabel(packageManager).toString();
            Drawable icon = info.loadIcon(packageManager);
            apps.add(new AppEntry(label, aInfo.packageName, aInfo.name, icon));
        }

        Collections.sort(apps, (a, b) -> a.label.toLowerCase().compareTo(b.label.toLowerCase()));
    }

    private void loadDesktopShortcuts() {
        desktopShortcuts.clear();
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String ordered = sp.getString(PREF_DESKTOP_SHORTCUTS_ORDERED, null);

        List<String> pkgNames = new ArrayList<>();
        if (ordered != null && !ordered.isEmpty()) {
            for (String p : ordered.split(",")) {
                if (!p.trim().isEmpty()) {
                    pkgNames.add(p.trim());
                }
            }
        } else {
            Set<String> set = sp.getStringSet(PREF_DESKTOP_SHORTCUTS, null);
            if (set != null && !set.isEmpty()) {
                pkgNames.addAll(set);
            }
        }

        // Defaults if first run
        if (pkgNames.isEmpty()) {
            pkgNames.add("com.android.chrome");
            pkgNames.add("com.google.android.youtube");
            pkgNames.add("com.android.settings");
            pkgNames.add("com.google.android.apps.docs");
        }

        for (String pkg : pkgNames) {
            AppEntry app = findApp(pkg);
            if (app != null && !desktopShortcuts.contains(app)) {
                desktopShortcuts.add(app);
            }
        }
    }

    private void saveDesktopShortcuts() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        StringBuilder sb = new StringBuilder();
        Set<String> set = new LinkedHashSet<>();
        for (int i = 0; i < desktopShortcuts.size(); i++) {
            String pkg = desktopShortcuts.get(i).packageName;
            set.add(pkg);
            if (i > 0) sb.append(",");
            sb.append(pkg);
        }
        editor.putString(PREF_DESKTOP_SHORTCUTS_ORDERED, sb.toString());
        editor.putStringSet(PREF_DESKTOP_SHORTCUTS, set);
        editor.apply();
    }

    private AppEntry findApp(String packageName) {
        for (AppEntry app : apps) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    private void refreshDesktopShortcuts() {
        if (desktopShortcutContainer == null) return;
        desktopShortcutContainer.removeAllViews();
        for (AppEntry app : desktopShortcuts) {
            desktopShortcutContainer.addView(makeDesktopShortcutView(app));
        }
    }

    private View makeDesktopShortcutView(AppEntry app) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(6), dp(8), dp(6), dp(8));
        item.setBackground(roundedDrawable(Color.TRANSPARENT, 6));

        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(app.icon);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        item.addView(iconView, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView labelView = new TextView(this);
        labelView.setText(app.label);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(11f);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(2);
        labelView.setPadding(0, dp(4), 0, 0);
        item.addView(labelView, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));

        item.setOnClickListener(v -> launchOnThisDisplay(app, MODE_NORMAL));
        item.setOnLongClickListener(v -> {
            desktopShortcuts.remove(app);
            saveDesktopShortcuts();
            refreshDesktopShortcuts();
            Toast.makeText(this, "Shortcut dihapus: " + app.label, Toast.LENGTH_SHORT).show();
            return true;
        });

        return item;
    }

    public void addShortcutToDesktop(AppEntry app) {
        for (AppEntry e : desktopShortcuts) {
            if (e.packageName.equals(app.packageName)) {
                Toast.makeText(this, "Shortcut sudah ada di desktop", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        desktopShortcuts.add(app);
        saveDesktopShortcuts();
        refreshDesktopShortcuts();
        Toast.makeText(this, "Ditambahkan ke desktop: " + app.label, Toast.LENGTH_SHORT).show();
    }

    private void pushRecentApp(AppEntry app) {
        recentApps.remove(app);
        recentApps.add(0, app);
        if (recentApps.size() > 8) {
            recentApps.remove(recentApps.size() - 1);
        }
        refreshRecentTaskbar();
    }

    private void refreshRecentTaskbar() {
        if (recentTaskbarStrip == null) return;
        recentTaskbarStrip.removeAllViews();
        for (AppEntry app : recentApps) {
            recentTaskbarStrip.addView(makeTaskbarAppButton(app));
        }
    }

    private View makeTaskbarAppButton(AppEntry app) {
        FrameLayout container = new FrameLayout(this);
        int pad = dp(4);
        container.setPadding(pad, dp(2), pad, 0);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setBackground(roundedDrawable(Color.TRANSPARENT, 6));

        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(app.icon);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        iconParams.topMargin = dp(4);
        content.addView(iconView, iconParams);

        // Samsung DeX Active Indicator Line below app icon
        View indicator = new View(this);
        indicator.setBackground(roundedDrawable(Color.argb(0xDD, 0xFF, 0xFF, 0xFF), 1));
        LinearLayout.LayoutParams indParams = new LinearLayout.LayoutParams(dp(16), dp(2));
        indParams.topMargin = dp(3);
        content.addView(indicator, indParams);

        container.addView(content, new FrameLayout.LayoutParams(dp(44), dp(44)));

        container.setOnClickListener(v -> launchOnThisDisplay(app, MODE_NORMAL));
        container.setOnLongClickListener(v -> {
            showAppControls(app, container);
            return true;
        });

        return container;
    }

    // =========================================================================
    // SAMSUNG DeX TASKBAR COMPONENTS
    // =========================================================================

    /**
     * Samsung DeX App Drawer Button (3x3 grid dots icon)
     */
    private FrameLayout createDexAppsButton() {
        FrameLayout btn = new FrameLayout(this);
        btn.setBackground(roundedDrawable(Color.TRANSPARENT, 6));

        LinearLayout grid3x3 = new LinearLayout(this);
        grid3x3.setOrientation(LinearLayout.VERTICAL);
        grid3x3.setGravity(Gravity.CENTER);

        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int c = 0; c < 3; c++) {
                View dot = new View(this);
                dot.setBackground(roundedDrawable(Color.WHITE, 2));
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(3), dp(3));
                dotParams.setMargins(dp(1), dp(1), dp(1), dp(1));
                row.addView(dot, dotParams);
            }
            grid3x3.addView(row);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER;
        btn.addView(grid3x3, lp);

        btn.setOnClickListener(v -> toggleStartMenu());
        return btn;
    }

    /**
     * Samsung DeX Search Icon (Finder)
     */
    private FrameLayout createDexSearchButton() {
        FrameLayout btn = new FrameLayout(this);
        btn.setBackground(roundedDrawable(Color.TRANSPARENT, 6));

        TextView tv = new TextView(this);
        tv.setText("🔍");
        tv.setTextSize(14f);
        tv.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(32), dp(32));
        lp.gravity = Gravity.CENTER;
        btn.addView(tv, lp);

        btn.setOnClickListener(v -> toggleStartMenu());
        return btn;
    }

    /**
     * Samsung DeX Navigation Buttons: Recent Apps (|||), Home (○), Back (<)
     */
    private LinearLayout createDexNavButtons() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);

        // 1. Recent Apps (|||)
        FrameLayout recentsBtn = new FrameLayout(this);
        recentsBtn.setBackground(roundedDrawable(Color.TRANSPARENT, 6));
        LinearLayout recentsIcon = new LinearLayout(this);
        recentsIcon.setOrientation(LinearLayout.HORIZONTAL);
        recentsIcon.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            View bar = new View(this);
            bar.setBackground(roundedDrawable(Color.WHITE, 1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(2), dp(13));
            p.setMargins(dp(1), 0, dp(1), 0);
            recentsIcon.addView(bar, p);
        }
        FrameLayout.LayoutParams recParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recParams.gravity = Gravity.CENTER;
        recentsBtn.addView(recentsIcon, recParams);
        recentsBtn.setOnClickListener(v -> {
            if (!recentApps.isEmpty()) {
                showAppControls(recentApps.get(0), recentsBtn);
            }
        });

        // 2. Home (○)
        FrameLayout homeBtn = new FrameLayout(this);
        homeBtn.setBackground(roundedDrawable(Color.TRANSPARENT, 6));
        View circle = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(2), Color.WHITE);
        ring.setColor(Color.TRANSPARENT);
        circle.setBackground(ring);
        FrameLayout.LayoutParams homeCircleParams = new FrameLayout.LayoutParams(dp(13), dp(13));
        homeCircleParams.gravity = Gravity.CENTER;
        homeBtn.addView(circle, homeCircleParams);
        homeBtn.setOnClickListener(v -> bringLauncherToFront());

        // 3. Back (<)
        FrameLayout backBtn = new FrameLayout(this);
        backBtn.setBackground(roundedDrawable(Color.TRANSPARENT, 6));
        TextView backText = new TextView(this);
        backText.setText("<");
        backText.setTextColor(Color.WHITE);
        backText.setTextSize(15f);
        backText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backParams.gravity = Gravity.CENTER;
        backBtn.addView(backText, backParams);
        backBtn.setOnClickListener(v -> onBackPressed());

        int btnWidth = dp(32);
        int btnHeight = dp(36);
        nav.addView(recentsBtn, new LinearLayout.LayoutParams(btnWidth, btnHeight));
        nav.addView(homeBtn, new LinearLayout.LayoutParams(btnWidth, btnHeight));
        nav.addView(backBtn, new LinearLayout.LayoutParams(btnWidth, btnHeight));

        return nav;
    }

    /**
     * Samsung DeX Right System Tray / Control Center
     */
    private LinearLayout createDexSystemTray() {
        LinearLayout tray = new LinearLayout(this);
        tray.setOrientation(LinearLayout.HORIZONTAL);
        tray.setGravity(Gravity.CENTER_VERTICAL);
        tray.setPadding(0, 0, dp(6), 0);

        // Language Capsule [ID]
        TextView langText = new TextView(this);
        langText.setText("ID");
        langText.setTextColor(Color.WHITE);
        langText.setTextSize(10f);
        langText.setGravity(Gravity.CENTER);
        langText.setPadding(dp(5), dp(1), dp(5), dp(1));
        langText.setBackground(roundedDrawable(PANEL_2, 4));
        LinearLayout.LayoutParams langParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        langParams.setMargins(dp(2), 0, dp(4), 0);
        tray.addView(langText, langParams);

        // Action Icons: Keyboard [⌨], Screenshot [📷], Volume [🔇], Notif [💬●]
        String[] icons = {"⌨", "📷", "🔇", "💬"};
        for (String ic : icons) {
            TextView iconView = new TextView(this);
            iconView.setText(ic);
            iconView.setTextColor(Color.WHITE);
            iconView.setTextSize(12f);
            iconView.setGravity(Gravity.CENTER);
            iconView.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(26), dp(32));
            tray.addView(iconView, p);
        }

        // Status Cluster Pill (Wi-Fi, Battery, etc.)
        LinearLayout statusPill = new LinearLayout(this);
        statusPill.setOrientation(LinearLayout.HORIZONTAL);
        statusPill.setGravity(Gravity.CENTER_VERTICAL);
        statusPill.setPadding(dp(6), dp(2), dp(6), dp(2));
        statusPill.setBackground(roundedDrawable(PANEL_3, 12));

        // Wi-Fi icon
        TextView wifi = new TextView(this);
        wifi.setText("📶");
        wifi.setTextSize(10f);
        wifi.setPadding(0, 0, dp(4), 0);
        statusPill.addView(wifi);

        // Battery percent & icon
        batteryPercentTextView = new TextView(this);
        batteryPercentTextView.setText("100% 🔋");
        batteryPercentTextView.setTextColor(Color.WHITE);
        batteryPercentTextView.setTextSize(10f);
        batteryPercentTextView.setGravity(Gravity.CENTER_VERTICAL);
        statusPill.addView(batteryPercentTextView);

        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.setMargins(dp(4), 0, dp(6), 0);
        tray.addView(statusPill, pillParams);

        // 2-Line TextClock (Line 1: HH:mm, Line 2: EEE, d/M)
        LinearLayout clockContainer = new LinearLayout(this);
        clockContainer.setOrientation(LinearLayout.VERTICAL);
        clockContainer.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        TextClock timeClock = new TextClock(this);
        timeClock.setFormat12Hour("HH:mm");
        timeClock.setFormat24Hour("HH:mm");
        timeClock.setTextColor(Color.WHITE);
        timeClock.setTextSize(11f);
        timeClock.setGravity(Gravity.END);
        clockContainer.addView(timeClock);

        TextClock dateClock = new TextClock(this);
        dateClock.setFormat12Hour("EEE, d/M");
        dateClock.setFormat24Hour("EEE, d/M");
        dateClock.setTextColor(MUTED);
        dateClock.setTextSize(9f);
        dateClock.setGravity(Gravity.END);
        clockContainer.addView(dateClock);

        tray.addView(clockContainer);

        return tray;
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0 && batteryPercentTextView != null) {
                    int pct = Math.round((level / (float) scale) * 100);
                    batteryPercentTextView.setText(pct + "% 🔋");
                }
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    // =========================================================================
    // DESKTOP & UI BUILDING
    // =========================================================================

    private void buildDesktopUi() {
        root = new FrameLayout(this);

        // 1. Wallpaper
        ImageView wallpaper = new ImageView(this);
        wallpaper.setImageResource(R.drawable.desktop_wallpaper);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Subtle Dark Screen Tint
        View tint = new View(this);
        tint.setBackgroundColor(Color.argb(0x18, 0x00, 0x00, 0x00));
        root.addView(tint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. Desktop Work Area (above taskbar)
        desktopArea = new FrameLayout(this);
        FrameLayout.LayoutParams desktopLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        desktopLp.bottomMargin = dp(48); // Height of taskbar
        root.addView(desktopArea, desktopLp);

        // Desktop Shortcuts Grid / Column
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setBackgroundColor(Color.TRANSPARENT);

        desktopShortcutContainer = new LinearLayout(this);
        desktopShortcutContainer.setOrientation(LinearLayout.VERTICAL);
        desktopShortcutContainer.setGravity(Gravity.START | Gravity.TOP);
        desktopShortcutContainer.setPadding(dp(12), dp(16), dp(12), dp(16));
        sv.addView(desktopShortcutContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        desktopArea.addView(sv, new FrameLayout.LayoutParams(
                dp(100), ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. Samsung DeX Taskbar Bar
        LinearLayout taskbar = new LinearLayout(this);
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding(dp(6), 0, dp(8), 0);
        taskbar.setBackgroundColor(TASKBAR); // Flat sleek bottom bar

        // [Left Section] Apps + Search + Nav Keys
        startButton = createDexAppsButton();
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        startLp.setMargins(dp(2), 0, dp(2), 0);
        taskbar.addView(startButton, startLp);

        FrameLayout searchBtn = createDexSearchButton();
        taskbar.addView(searchBtn, new LinearLayout.LayoutParams(dp(32), dp(36)));

        LinearLayout navBtns = createDexNavButtons();
        taskbar.addView(navBtns, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        // [Center Section] Running / Recent Apps
        recentTaskbarStrip = new LinearLayout(this);
        recentTaskbarStrip.setOrientation(LinearLayout.HORIZONTAL);
        recentTaskbarStrip.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(0, dp(44), 1.0f);
        stripParams.setMargins(dp(8), 0, dp(8), 0);
        taskbar.addView(recentTaskbarStrip, stripParams);

        // [Right Section] System Tray & Date/Time
        LinearLayout tray = createDexSystemTray();
        taskbar.addView(tray, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Add taskbar docked at bottom
        FrameLayout.LayoutParams taskbarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        taskbarLp.gravity = Gravity.BOTTOM;
        root.addView(taskbar, taskbarLp);

        // 5. Popup Menus
        buildStartMenu();
        buildAppControlLayer();

        setContentView(root);
    }

    private void buildStartMenu() {
        startMenuLayer = new FrameLayout(this);
        startMenuLayer.setVisibility(View.GONE);

        View overlay = new View(this);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(v -> hideStartMenu());
        startMenuLayer.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundedDrawable(PANEL, 12));

        TextView header = new TextView(this);
        header.setText("All apps  •  tahan app untuk Add to Desktop");
        header.setTextColor(MUTED);
        header.setTextSize(11f);
        header.setPadding(0, 0, 0, dp(12));
        card.addView(header);

        startMenuGrid = new GridView(this);
        startMenuGrid.setNumColumns(5);
        startMenuGrid.setHorizontalSpacing(dp(8));
        startMenuGrid.setVerticalSpacing(dp(8));
        startMenuGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        startMenuGrid.setAdapter(new StartMenuAdapter(this, apps, dp(6), dp(44)));
        startMenuGrid.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry app = apps.get(position);
            hideStartMenu();
            launchOnThisDisplay(app, MODE_NORMAL);
        });
        startMenuGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            AppEntry app = apps.get(position);
            addShortcutToDesktop(app);
            return true;
        });

        card.addView(startMenuGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(440), dp(420));
        cardLp.gravity = Gravity.BOTTOM | Gravity.START;
        cardLp.leftMargin = dp(8);
        cardLp.bottomMargin = dp(56);
        startMenuLayer.addView(card, cardLp);

        root.addView(startMenuLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void toggleStartMenu() {
        if (startMenuVisible) {
            hideStartMenu();
        } else {
            showStartMenu();
        }
    }

    private void showStartMenu() {
        startMenuVisible = true;
        startMenuLayer.setVisibility(View.VISIBLE);
        if (startButton != null) {
            startButton.setBackground(roundedDrawable(PANEL_2, 6));
        }
    }

    private void hideStartMenu() {
        startMenuVisible = false;
        if (startMenuLayer != null) {
            startMenuLayer.setVisibility(View.GONE);
        }
        if (startButton != null) {
            startButton.setBackground(roundedDrawable(Color.TRANSPARENT, 6));
        }
    }

    // =========================================================================
    // WINDOW CONTROLS POPUP
    // =========================================================================

    private void buildAppControlLayer() {
        appControlLayer = new FrameLayout(this);
        appControlLayer.setVisibility(View.GONE);

        View overlay = new View(this);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(v -> hideAppControls());
        appControlLayer.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(appControlLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showAppControls(AppEntry app, View anchor) {
        activeApp = app;
        appControlLayer.removeAllViews();

        View overlay = new View(this);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(v -> hideAppControls());
        appControlLayer.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(roundedDrawable(PANEL, 8));

        TextView title = new TextView(this);
        title.setText(app.label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(12f);
        title.setPadding(dp(6), dp(4), dp(6), dp(6));
        card.addView(title);

        card.addView(makeControlButton("Focus / Open", () -> {
            hideAppControls();
            launchOnThisDisplay(app, MODE_NORMAL);
        }));
        card.addView(makeControlButton("Maximize", () -> {
            hideAppControls();
            launchOnThisDisplay(app, MODE_MAXIMIZED);
        }));
        card.addView(makeControlButton("Minimize", () -> {
            hideAppControls();
            bringLauncherToFront();
        }));
        card.addView(makeControlButton("Close App", () -> {
            hideAppControls();
            closeAppBestEffort(app);
        }));

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.BOTTOM | Gravity.START;
        cardLp.leftMargin = Math.max(dp(8), loc[0] - dp(30));
        cardLp.bottomMargin = dp(54);
        appControlLayer.addView(card, cardLp);

        appControlLayer.setVisibility(View.VISIBLE);
    }

    private View makeControlButton(String label, Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(MUTED);
        btn.setTextSize(11f);
        btn.setPadding(dp(8), dp(6), dp(8), dp(6));
        btn.setBackground(roundedDrawable(Color.TRANSPARENT, 4));
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private void hideAppControls() {
        if (appControlLayer != null) {
            appControlLayer.setVisibility(View.GONE);
        }
    }

    // =========================================================================
    // LAUNCHING & TASK LOGIC
    // =========================================================================

    public void launchOnThisDisplay(AppEntry app, int mode) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunchElapsed < LAUNCH_DEBOUNCE_MS) {
            return;
        }
        lastLaunchElapsed = now;

        try {
            // Special case for Settings
            if ("com.android.settings".equals(app.packageName)) {
                Toast.makeText(this, "Settings dibuka di layar HP agar DroidUP Dex tidak black screen", Toast.LENGTH_SHORT).show();
            }

            Intent intent = packageManager.getLaunchIntentForPackage(app.packageName);
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setClassName(app.packageName, app.activityName);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(currentDisplayId);

            Rect bounds = createLaunchBounds(mode);
            if (bounds != null) {
                options.setLaunchBounds(bounds);
            }

            startActivity(intent, options.toBundle());
            pushRecentApp(app);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka " + app.label + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Rect createLaunchBounds(int mode) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels - dp(48); // subtract taskbar

        if (mode == MODE_MAXIMIZED) {
            return new Rect(0, 0, screenW, screenH);
        } else if (mode == MODE_COMPACT) {
            int w = Math.round(screenW * 0.45f);
            int h = Math.round(screenH * 0.70f);
            int l = (screenW - w) / 2;
            int t = (screenH - h) / 2;
            return new Rect(l, t, l + w, t + h);
        } else {
            int w = Math.round(screenW * 0.70f);
            int h = Math.round(screenH * 0.80f);
            int l = (screenW - w) / 2;
            int t = (screenH - h) / 2;
            return new Rect(l, t, l + w, t + h);
        }
    }

    public void bringLauncherToFront() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(currentDisplayId);
        startActivity(intent, options.toBundle());
    }

    public void closeAppBestEffort(AppEntry app) {
        bringLauncherToFront();
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(app.packageName);
            }
            recentApps.remove(app);
            refreshRecentTaskbar();
            Toast.makeText(this, "Close app: " + app.label, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa menutup app ini", Toast.LENGTH_SHORT).show();
        }
    }
}
