package com.example.dictpenlauncher;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private RecyclerView mainGrid, drawerGrid;
    private View drawerLayout;
    private Button btnToggle;
    private List<AppAdapter.AppItem> allApps = new ArrayList<>();
    private List<AppAdapter.AppItem> mainApps = new ArrayList<>();
    private SharedPreferences sp;

    // 动态时间相关
    private TextView tvTime;
    private Handler handler = new Handler();
    private Runnable timeRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("launcher", 0);
        mainGrid = findViewById(R.id.main_grid);
        drawerGrid = findViewById(R.id.drawer_grid);
        drawerLayout = findViewById(R.id.drawer_layout);
        btnToggle = findViewById(R.id.btn_toggle);
        tvTime = findViewById(R.id.tv_time);          // 获取时间控件

        // 优化 RecyclerView 滑动性能
        mainGrid.setHasFixedSize(true);
        drawerGrid.setHasFixedSize(true);

        mainGrid.setLayoutManager(new GridLayoutManager(this, 4));
        drawerGrid.setLayoutManager(new GridLayoutManager(this, 4));

        loadAllApps();    // 会预缓存图标和文字
        loadMainApps();
        refreshMain();
        refreshDrawer();

        // 启动动态时间（每秒刷新，时分秒三行）
        startTimeUpdate();

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (drawerLayout.getVisibility() == View.GONE) {
                    drawerLayout.setVisibility(View.VISIBLE);
                    btnToggle.setText("返回\n主屏");
                } else {
                    drawerLayout.setVisibility(View.GONE);
                    btnToggle.setText("全部\n应用");
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止时间更新，防止内存泄漏
        if (handler != null && timeRunnable != null) {
            handler.removeCallbacks(timeRunnable);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.getVisibility() == View.VISIBLE) {
            drawerLayout.setVisibility(View.GONE);
            btnToggle.setText("全部\n应用");
        }
    }

    /**
     * 加载系统中所有可启动的应用，并预缓存图标和名称
     */
    private void loadAllApps() {
        allApps.clear();

        // 添加快捷方式（“添加网页”按钮）
        AppAdapter.AppItem addItem = new AppAdapter.AppItem();
        addItem.type = "add";
        allApps.add(addItem);

        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo info : list) {
            AppAdapter.AppItem item = new AppAdapter.AppItem();
            item.type = "app";
            item.info = info;
            // 预加载图标和文字并缓存（关键优化，避免滑动卡顿）
            item.cachedIcon = info.loadIcon(pm);
            item.cachedLabel = info.loadLabel(pm).toString();
            allApps.add(item);
        }
    }

    private void loadMainApps() {
        mainApps.clear();
        Set<String> saved = sp.getStringSet("main", new HashSet<String>());
        for (String s : saved) {
            String[] a = s.split("\\|", 2);
            if (a[0].equals("app")) {
                for (AppAdapter.AppItem all : allApps) {
                    if (all.type.equals("app") && all.info.activityInfo.packageName.equals(a[1])) {
                        mainApps.add(all);
                        break;
                    }
                }
            } else if (a[0].equals("url")) {
                String[] u = a[1].split("\\|", 3);
                AppAdapter.AppItem item = new AppAdapter.AppItem();
                item.type = "url";
                item.title = u[0];
                item.url = u[1];
                item.browserPkg = u.length > 2 ? u[2] : "";
                mainApps.add(item);
            }
        }
    }

    private void saveMain() {
        Set<String> set = new HashSet<String>();
        for (AppAdapter.AppItem item : mainApps) {
            if (item.type.equals("app"))
                set.add("app|" + item.info.activityInfo.packageName);
            else if (item.type.equals("url"))
                set.add("url|" + item.title + "|" + item.url + "|" + item.browserPkg);
        }
        sp.edit().putStringSet("main", set).apply();
    }

    private void refreshMain() {
        mainGrid.setAdapter(new AppAdapter(this, mainApps, new AppAdapter.OnClick() {
            @Override
            public void onItemClick(AppAdapter.AppItem item) {
                launch(item);
            }

            @Override
            public void onItemLongClick(AppAdapter.AppItem item, int pos) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("从主屏移除？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            mainApps.remove(pos);
                            saveMain();
                            refreshMain();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }));
    }

    private void refreshDrawer() {
        drawerGrid.setAdapter(new AppAdapter(this, allApps, new AppAdapter.OnClick() {
            @Override
            public void onItemClick(AppAdapter.AppItem item) {
                if (item.type.equals("add")) {
                    showAddDialog();
                } else {
                    launch(item);
                    drawerLayout.setVisibility(View.GONE);
                    btnToggle.setText("全部\n应用");
                }
            }

            @Override
            public void onItemLongClick(AppAdapter.AppItem item, int pos) {
                if (item.type.equals("app") || item.type.equals("url")) {
                    mainApps.add(item);
                    saveMain();
                    refreshMain();
                    Toast.makeText(MainActivity.this, "已添加到主屏", Toast.LENGTH_SHORT).show();
                }
            }
        }));
    }

    private void launch(AppAdapter.AppItem item) {
        try {
            if (item.type.equals("app")) {
                Intent i = getPackageManager().getLaunchIntentForPackage(item.info.activityInfo.packageName);
                startActivity(i);
            } else {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
                if (item.browserPkg != null && !item.browserPkg.isEmpty()) {
                    i.setPackage(item.browserPkg);
                }
                startActivity(i);
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法启动", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_url, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        final EditText name = v.findViewById(R.id.name);
        final EditText url = v.findViewById(R.id.url);
        final EditText browser = v.findViewById(R.id.browser);

        builder.setPositiveButton("添加", (dialog, which) -> {
            String n = name.getText().toString().trim();
            String u = url.getText().toString().trim();
            if (n.isEmpty() || u.isEmpty()) return;
            AppAdapter.AppItem item = new AppAdapter.AppItem();
            item.type = "url";
            item.title = n;
            item.url = u;
            item.browserPkg = browser.getText().toString().trim();
            mainApps.add(item);
            saveMain();
            refreshMain();
            Toast.makeText(this, "已添加到主屏", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 动态时间更新：时、分、秒各占一行，加粗显示
     */
    private void startTimeUpdate() {
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
                int minute = calendar.get(java.util.Calendar.MINUTE);
                int second = calendar.get(java.util.Calendar.SECOND);
                String timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hour, minute, second);
                tvTime.setText(timeStr);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timeRunnable);
    }
}