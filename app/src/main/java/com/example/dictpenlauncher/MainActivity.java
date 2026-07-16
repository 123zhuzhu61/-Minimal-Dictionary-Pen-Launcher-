package com.example.dictpenlauncher;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // ---- 视图 ----
    private RecyclerView mainGrid, drawerGrid;
    private View drawerLayout;
    private Button btnToggle;

    // ---- 数据 ----
    /** 全部应用（含字母头部item），带排序 */
    private final List<AppAdapter.AppItem> allApps = new ArrayList<>();
    /** 主屏应用（无字母头） */
    private final List<AppAdapter.AppItem> mainApps = new ArrayList<>();
    private SharedPreferences sp;

    // ---- 适配器 ----
    private AppAdapter mainAdapter;
    private AppAdapter drawerAdapter;

    // ---- 编辑模式 ----
    private boolean isEditMode = false;
    /** 进入/退出编辑模式前，记录 drawerGrid 的滚动位置 */
    private int savedScrollPos    = 0;
    private int savedScrollOffset = 0;

    // ---- 应用数量缓存（用于检测新增/卸载） ----
    private int lastAllAppsCount = 0;

    // ---- WiFi ----
    private WifiManager wifiManager;

    // ---- 控制中心 ----
    private ControlCenterManager ccManager;

    // ---- 左→右滑手势检测（全屏任意位置） ----
    private float ccTouchStartX = -1;
    private float ccTouchStartY = -1;
    /** 触发控制中心的最小向右滑动距离（dp转px在运行时计算） */
    private static final int CC_SWIPE_DP = 60;

    // ---- 权限 ----
    private static final int REQUEST_LOCATION_PERMISSION = 100;

    // ---- 广播 ----
    private final BroadcastReceiver appInstallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshAllAppsIfNeeded();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("launcher", 0);

        // 视图绑定
        mainGrid     = findViewById(R.id.main_grid);
        drawerGrid   = findViewById(R.id.drawer_grid);
        drawerLayout = findViewById(R.id.drawer_layout);
        btnToggle    = findViewById(R.id.btn_toggle);

        // 布局管理器（4列，狭长屏）
        GridLayoutManager mainGlm   = new GridLayoutManager(this, 4);
        GridLayoutManager drawerGlm = new GridLayoutManager(this, 4);
        mainGrid.setLayoutManager(mainGlm);
        drawerGrid.setLayoutManager(drawerGlm);
        mainGrid.setHasFixedSize(false);
        drawerGrid.setHasFixedSize(false);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // 权限申请
        checkAndRequestLocationPermission();

        // 加载数据
        loadAllApps();
        loadMainApps();

        // 初始化适配器
        setupMainAdapter();
        setupDrawerAdapter();

        // 初始化控制中心
        ccManager = new ControlCenterManager(this, sp);
        ccManager.init();

        // 注册应用安装/卸载广播
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addDataScheme("package");
        registerReceiver(appInstallReceiver, pkgFilter);

        // 左侧按钮
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isEditMode) {
                    exitEditMode();
                } else if (drawerLayout.getVisibility() == View.GONE) {
                    refreshAllAppsIfNeeded();
                    drawerLayout.setVisibility(View.VISIBLE);
                    btnToggle.setText("返回\n主屏");
                } else {
                    drawerLayout.setVisibility(View.GONE);
                    btnToggle.setText("全部\n应用");
                }
            }
        });
    }

    // ====================================================================
    //  触摸事件：全屏任意位置向右滑动 → 呼出控制中心
    //            控制中心显示时，从左边缘向左滑 → 关闭控制中心
    // ====================================================================
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float threshold = CC_SWIPE_DP * getResources().getDisplayMetrics().density;
        float edgeZone = 40 * getResources().getDisplayMetrics().density; // 边缘区域 40dp

        // 控制中心显示时，从左侧边缘向左滑动可关闭
        if (ccManager != null && ccManager.isShowing()) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 只在左侧边缘开始触摸才记录
                    if (ev.getRawX() <= edgeZone) {
                        ccTouchStartX = ev.getRawX();
                        ccTouchStartY = ev.getRawY();
                    } else {
                        ccTouchStartX = -1;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (ccTouchStartX >= 0) {
                        float dx = ccTouchStartX - ev.getRawX(); // 向左为正
                        float dy = Math.abs(ev.getRawY() - ccTouchStartY);
                        if (dx > threshold && dx > dy) {
                            ccTouchStartX = -1;
                            ccManager.hide();
                            return true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    ccTouchStartX = -1;
                    break;
            }
            return super.dispatchTouchEvent(ev);
        }

        // 控制中心未显示时，全屏任意位置向右滑动呼出
        if (ccManager != null && ccManager.isCcEnabled()) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    ccTouchStartX = ev.getRawX();
                    ccTouchStartY = ev.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (ccTouchStartX >= 0) {
                        float dx = ev.getRawX() - ccTouchStartX; // 向右为正
                        float dy = Math.abs(ev.getRawY() - ccTouchStartY);
                        if (dx > threshold && dx > dy) {
                            ccTouchStartX = -1;
                            ccManager.show();
                            return true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    ccTouchStartX = -1;
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ====================================================================
    //  返回键
    // ====================================================================
    @Override
    public void onBackPressed() {
        if (ccManager != null && ccManager.isShowing()) {
            ccManager.hide();
        } else if (isEditMode) {
            exitEditMode();
        } else if (drawerLayout.getVisibility() == View.VISIBLE) {
            drawerLayout.setVisibility(View.GONE);
            btnToggle.setText("全部\n应用");
        }
        // 主屏不退出
    }

    // ====================================================================
    //  生命周期
    // ====================================================================
    @Override
    protected void onResume() {
        super.onResume();
        if (ccManager != null && ccManager.isShowing()) {
            ccManager.hide();
        }
        refreshAllAppsIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ccManager != null) ccManager.destroy();
        try { unregisterReceiver(appInstallReceiver); } catch (Exception ignored) {}
    }

    // ====================================================================
    //  权限
    // ====================================================================
    private void checkAndRequestLocationPermission() {
        if (sp.getBoolean("location_permission_asked", false)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION_PERMISSION);
            }
        }
        sp.edit().putBoolean("location_permission_asked", true).apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ====================================================================
    //  加载全部应用（带拼音排序 & 字母分组头）
    // ====================================================================
    private void loadAllApps() {
        allApps.clear();

        // 固定功能项（排在最前，不带字母头）
        allApps.add(makeSpecial("add_web"));
        allApps.add(makeSpecial("add_app"));
        allApps.add(makeSpecial("cc_settings"));
        allApps.add(makeSpecial("about"));

        // 扫描系统已安装应用
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);

        List<AppAdapter.AppItem> appItems = new ArrayList<>();
        for (ResolveInfo info : list) {
            AppAdapter.AppItem item = new AppAdapter.AppItem();
            item.type        = "app";
            item.info        = info;
            item.cachedIcon  = info.loadIcon(pm);
            item.cachedLabel = info.loadLabel(pm).toString();
            appItems.add(item);
        }

        // 按拼音排序
        Collections.sort(appItems, new Comparator<AppAdapter.AppItem>() {
            @Override
            public int compare(AppAdapter.AppItem a, AppAdapter.AppItem b) {
                return PinyinUtils.getSortKey(a.cachedLabel)
                        .compareToIgnoreCase(PinyinUtils.getSortKey(b.cachedLabel));
            }
        });

        // 插入字母分组头
        char lastLetter = 0;
        for (AppAdapter.AppItem item : appItems) {
            String lbl = item.cachedLabel != null ? item.cachedLabel : "";
            char letter = PinyinUtils.getFirstLetter(lbl.isEmpty() ? "" : lbl);
            if (letter != lastLetter) {
                AppAdapter.AppItem header = new AppAdapter.AppItem();
                header.type         = "letter_header";
                header.headerLetter = (letter == '#') ? "#" : String.valueOf(letter);
                allApps.add(header);
                lastLetter = letter;
            }
            allApps.add(item);
        }

        lastAllAppsCount = getActualAppCount();
    }

    private AppAdapter.AppItem makeSpecial(String type) {
        AppAdapter.AppItem item = new AppAdapter.AppItem();
        item.type = type;
        return item;
    }

    /** 获取真实应用（非字母头）的数量 */
    private int getActualAppCount() {
        int count = 0;
        for (AppAdapter.AppItem item : allApps) {
            if (!"letter_header".equals(item.type)) count++;
        }
        return count;
    }

    // ====================================================================
    //  检测新增/卸载，按需刷新
    // ====================================================================
    private void refreshAllAppsIfNeeded() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        int currentCount = pm.queryIntentActivities(intent, 0).size() + 4; // +4特殊项
        if (currentCount != lastAllAppsCount) {
            loadAllApps();
            cleanRemovedAppsFromMain();
            updateMainScreen();
            if (drawerAdapter != null) {
                drawerAdapter.notifyDataSetChanged();
            }
        }
    }

    // ====================================================================
    //  主屏适配器
    // ====================================================================
    private void setupMainAdapter() {
        mainAdapter = new AppAdapter(this, mainApps, true, new AppAdapter.OnClick() {
            @Override
            public void onItemClick(AppAdapter.AppItem item) {
                launch(item);
            }
            @Override
            public void onItemLongClick(AppAdapter.AppItem item, int pos) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("从主屏移除「" + getItemLabel(item) + "」？")
                        .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                mainApps.remove(item);
                                saveMain();
                                updateMainScreen();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        mainGrid.setAdapter(mainAdapter);
        mainAdapter.attachToRecyclerView(mainGrid);
    }

    private String getItemLabel(AppAdapter.AppItem item) {
        if ("app".equals(item.type)) return item.cachedLabel != null ? item.cachedLabel : "";
        if ("url".equals(item.type) || "custom_app".equals(item.type))
            return item.title != null ? item.title : "";
        return item.type;
    }

    private void updateMainScreen() {
        if (mainAdapter != null) mainAdapter.notifyDataSetChanged();
    }

    // ====================================================================
    //  抽屉（全部应用）适配器 —— 编辑模式与普通模式共用同一个 Adapter
    // ====================================================================
    private void setupDrawerAdapter() {
        drawerAdapter = new AppAdapter(this, allApps, false, new AppAdapter.OnClick() {
            @Override
            public void onItemClick(AppAdapter.AppItem item) {
                if (isEditMode) return; // 编辑模式下点击无效
                switch (item.type) {
                    case "add_web":
                        showAddWebDialog();
                        break;
                    case "add_app":
                        showAddCustomAppDialog();
                        break;
                    case "cc_settings":
                        if (ccManager != null) ccManager.showCcSettingsDialog();
                        break;
                    case "about":
                        showAboutDialog();
                        break;
                    default:
                        launch(item);
                        drawerLayout.setVisibility(View.GONE);
                        btnToggle.setText("全部\n应用");
                        break;
                }
            }
            @Override
            public void onItemLongClick(AppAdapter.AppItem item, int adapterPos) {
                if (!isEditMode && "app".equals(item.type)) {
                    enterEditModeAt(adapterPos);
                }
            }
        });

        drawerAdapter.setEditCallback(new AppAdapter.EditCallback() {
            @Override
            public void onAddToHome(AppAdapter.AppItem item) {
                if (!mainApps.contains(item)) {
                    mainApps.add(item);
                    saveMain();
                    updateMainScreen();
                    Toast.makeText(MainActivity.this, "已添加到主屏", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "已在主屏", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onUninstall(AppAdapter.AppItem item) {
                Intent i = new Intent(Intent.ACTION_DELETE);
                i.setData(Uri.parse("package:" + item.info.activityInfo.packageName));
                startActivity(i);
                // 卸载后通过广播自动刷新
            }
            @Override
            public boolean isSystemApp(AppAdapter.AppItem item) {
                try {
                    ApplicationInfo appInfo = getPackageManager()
                            .getApplicationInfo(item.info.activityInfo.packageName, 0);
                    return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                } catch (PackageManager.NameNotFoundException e) {
                    return false;
                }
            }
        });

        drawerGrid.setAdapter(drawerAdapter);
        drawerAdapter.attachToRecyclerView(drawerGrid);
    }

    // ====================================================================
    //  编辑模式 - 在原位置直接激活，不重建Adapter，无跳动
    // ====================================================================
    private void enterEditModeAt(int adapterPos) {
        isEditMode = true;

        // 记录当前滚动位置
        GridLayoutManager glm = (GridLayoutManager) drawerGrid.getLayoutManager();
        if (glm != null) {
            savedScrollPos    = glm.findFirstVisibleItemPosition();
            View firstView    = glm.findViewByPosition(savedScrollPos);
            savedScrollOffset = (firstView == null) ? 0 : firstView.getTop();
        }

        // 确保抽屉可见
        if (drawerLayout.getVisibility() == View.GONE) {
            drawerLayout.setVisibility(View.VISIBLE);
        }

        // 切换编辑模式（notifyDataSetChanged，保持位置不变）
        drawerAdapter.setEditMode(true);

        btnToggle.setText("完成");

        // 恢复滚动位置（防止 notifyDataSetChanged 后跳动）
        drawerGrid.post(new Runnable() {
            @Override
            public void run() {
                GridLayoutManager g = (GridLayoutManager) drawerGrid.getLayoutManager();
                if (g != null) {
                    g.scrollToPositionWithOffset(savedScrollPos, savedScrollOffset);
                }
            }
        });
    }

    private void exitEditMode() {
        // 记录退出时滚动位置
        GridLayoutManager glm = (GridLayoutManager) drawerGrid.getLayoutManager();
        if (glm != null) {
            savedScrollPos    = glm.findFirstVisibleItemPosition();
            View firstView    = glm.findViewByPosition(savedScrollPos);
            savedScrollOffset = (firstView == null) ? 0 : firstView.getTop();
        }

        isEditMode = false;
        drawerAdapter.setEditMode(false);

        btnToggle.setText(drawerLayout.getVisibility() == View.VISIBLE ? "返回\n主屏" : "全部\n应用");

        // 恢复滚动位置
        drawerGrid.post(new Runnable() {
            @Override
            public void run() {
                GridLayoutManager g = (GridLayoutManager) drawerGrid.getLayoutManager();
                if (g != null) {
                    g.scrollToPositionWithOffset(savedScrollPos, savedScrollOffset);
                }
            }
        });
    }

    // ====================================================================
    //  应用数据维护
    // ====================================================================
    private void loadMainApps() {
        mainApps.clear();
        String orderJson = sp.getString("main_order", null);
        if (orderJson == null) return;
        try {
            JSONArray jsonArray = new JSONArray(orderJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj  = jsonArray.getJSONObject(i);
                String     type = obj.getString("type");
                if ("app".equals(type)) {
                    String pkg = obj.getString("package");
                    for (AppAdapter.AppItem item : allApps) {
                        if ("app".equals(item.type)
                                && item.info.activityInfo.packageName.equals(pkg)) {
                            mainApps.add(item);
                            break;
                        }
                    }
                } else if ("url".equals(type)) {
                    AppAdapter.AppItem item = new AppAdapter.AppItem();
                    item.type       = "url";
                    item.title      = obj.getString("title");
                    item.url        = obj.getString("url");
                    item.browserPkg = obj.optString("browserPkg", "");
                    mainApps.add(item);
                } else if ("custom_app".equals(type)) {
                    AppAdapter.AppItem item = new AppAdapter.AppItem();
                    item.type        = "custom_app";
                    item.title       = obj.getString("title");
                    item.packageName = obj.getString("package");
                    item.className   = obj.getString("class");
                    mainApps.add(item);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveMain() {
        JSONArray jsonArray = new JSONArray();
        for (AppAdapter.AppItem item : mainApps) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", item.type);
                if ("app".equals(item.type)) {
                    obj.put("package", item.info.activityInfo.packageName);
                } else if ("url".equals(item.type)) {
                    obj.put("title", item.title);
                    obj.put("url", item.url);
                    obj.put("browserPkg", item.browserPkg != null ? item.browserPkg : "");
                } else if ("custom_app".equals(item.type)) {
                    obj.put("title", item.title);
                    obj.put("package", item.packageName);
                    obj.put("class", item.className);
                }
                jsonArray.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        sp.edit().putString("main_order", jsonArray.toString()).apply();
    }

    private void cleanRemovedAppsFromMain() {
        List<AppAdapter.AppItem> newMainApps = new ArrayList<>();
        for (AppAdapter.AppItem item : mainApps) {
            if ("url".equals(item.type)) {
                newMainApps.add(item);
            } else if ("app".equals(item.type)) {
                for (AppAdapter.AppItem ai : allApps) {
                    if ("app".equals(ai.type)
                            && ai.info.activityInfo.packageName.equals(
                                    item.info.activityInfo.packageName)) {
                        newMainApps.add(ai);
                        break;
                    }
                }
            } else if ("custom_app".equals(item.type)) {
                if (isCustomAppExists(item.packageName, item.className)) {
                    newMainApps.add(item);
                }
            }
        }
        mainApps.clear();
        mainApps.addAll(newMainApps);
        saveMain();
    }

    private boolean isCustomAppExists(String packageName, String className) {
        try {
            Intent intent = new Intent();
            intent.setClassName(packageName, className);
            return getPackageManager().resolveActivity(intent, 0) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ====================================================================
    //  启动应用
    // ====================================================================
    private void launch(AppAdapter.AppItem item) {
        try {
            if ("app".equals(item.type)) {
                Intent i = getPackageManager()
                        .getLaunchIntentForPackage(item.info.activityInfo.packageName);
                if (i != null) startActivity(i);
                else Toast.makeText(this, "无法启动", Toast.LENGTH_SHORT).show();
            } else if ("url".equals(item.type)) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
                if (item.browserPkg != null && !item.browserPkg.isEmpty()) {
                    i.setPackage(item.browserPkg);
                }
                startActivity(i);
            } else if ("custom_app".equals(item.type)) {
                Intent intent = new Intent();
                intent.setClassName(item.packageName, item.className);
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法启动", Toast.LENGTH_SHORT).show();
        }
    }

    // ====================================================================
    //  对话框
    // ====================================================================
    private void showAddWebDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_url, null);
        final EditText name    = v.findViewById(R.id.name);
        final EditText url     = v.findViewById(R.id.url);
        final EditText browser = v.findViewById(R.id.browser);
        new AlertDialog.Builder(this)
                .setView(v)
                .setPositiveButton("添加", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String n = name.getText().toString().trim();
                        String u = url.getText().toString().trim();
                        if (n.isEmpty() || u.isEmpty()) return;
                        AppAdapter.AppItem item = new AppAdapter.AppItem();
                        item.type       = "url";
                        item.title      = n;
                        item.url        = u;
                        item.browserPkg = browser.getText().toString().trim();
                        mainApps.add(item);
                        saveMain();
                        updateMainScreen();
                        Toast.makeText(MainActivity.this, "已添加到主屏", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddCustomAppDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_app, null);
        final EditText etName    = v.findViewById(R.id.custom_name);
        final EditText etPackage = v.findViewById(R.id.custom_package);
        final EditText etClass   = v.findViewById(R.id.custom_class);
        new AlertDialog.Builder(this)
                .setTitle("添加应用快捷方式")
                .setView(v)
                .setPositiveButton("添加", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String appName     = etName.getText().toString().trim();
                        String packageName = etPackage.getText().toString().trim();
                        String className   = etClass.getText().toString().trim();
                        if (appName.isEmpty() || packageName.isEmpty() || className.isEmpty()) {
                            Toast.makeText(MainActivity.this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            Intent intent = new Intent();
                            intent.setClassName(packageName, className);
                            if (getPackageManager().resolveActivity(intent, 0) != null) {
                                AppAdapter.AppItem item = new AppAdapter.AppItem();
                                item.type        = "custom_app";
                                item.title       = appName;
                                item.packageName = packageName;
                                item.className   = className;
                                mainApps.add(item);
                                saveMain();
                                updateMainScreen();
                                Toast.makeText(MainActivity.this, "已添加到主屏", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "应用不存在或类名错误", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "添加失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAboutDialog() {
        String version = "2.1";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        new AlertDialog.Builder(this)
                .setTitle("关于 DictPenLauncher")
                .setMessage("版本: " + version + "\n\n专为词典笔横屏设计的简洁桌面\n"
                        + "· 应用列表按拼音/字母自动分组排序\n"
                        + "· 全屏任意位置向右滑动可呼出控制中心\n"
                        + "· 在全部应用界面长按应用可进入编辑模式\n"
                        + "· 支持网页快捷方式、自定义应用")
                .setPositiveButton("确定", null)
                .show();
    }
}
