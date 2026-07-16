package com.example.dictpenlauncher;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 控制中心管理类
 * 从屏幕任意位置向右滑动呼出，只能向左滑动关闭
 * 全屏三栏布局：左开关（图标状态）、中时间、右水平药丸滑块
 */
public class ControlCenterManager {

    private final AppCompatActivity activity;
    private final SharedPreferences sp;
    private View ccRootView;   // 控制中心根视图（全屏覆盖）
    private View ccPanel;      // 主面板
    private View ccScrim;      // 蒙层（仅视觉，不可点击）
    private boolean isShowing = false;

    // 设置
    private boolean ccEnabled = true;
    private boolean is24HourFormat = true;
    private boolean showSeconds = false;

    // 视图引用
    private TextView ccTime, ccDate, ccBattery;
    private ImageView ccWifiIcon, ccBtIcon;
    private ImageView ccWifiImg, ccBtImg;  // 大图标
    private SeekBar seekBrightness, seekVolume;

    // 卡片容器（全区域可点击）
    private View wifiCard, btCard;
    // 中间区域滑动检测
    private View swipeArea;
    private float swipeStartX = -1;
    private static final float SWIPE_THRESHOLD_DP = 60f;

    private final Handler handler = new Handler();
    private Runnable timeRunnable;

    private WifiManager wifiManager;
    private AudioManager audioManager;

    // 广播接收器
    private BroadcastReceiver wifiStateReceiver;
    private BroadcastReceiver btStateReceiver;

    // 音量最大值（用于计算百分比）
    private int maxVolume = 15;

    public ControlCenterManager(AppCompatActivity activity, SharedPreferences sp) {
        this.activity = activity;
        this.sp = sp;
        this.wifiManager = (WifiManager) activity.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        loadSettings();
    }

    private void loadSettings() {
        ccEnabled = sp.getBoolean("cc_enabled", true);
        is24HourFormat = sp.getBoolean("cc_24hour", true);
        showSeconds = sp.getBoolean("cc_show_seconds", false);
    }

    private void saveSettings() {
        sp.edit()
                .putBoolean("cc_enabled", ccEnabled)
                .putBoolean("cc_24hour", is24HourFormat)
                .putBoolean("cc_show_seconds", showSeconds)
                .apply();
    }

    public boolean isCcEnabled() {
        return ccEnabled;
    }

    public void init() {
        ccRootView = LayoutInflater.from(activity).inflate(R.layout.control_center, null);
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        decor.addView(ccRootView, params);
        ccRootView.setVisibility(View.GONE);

        ccPanel        = ccRootView.findViewById(R.id.cc_panel);
        ccScrim        = ccRootView.findViewById(R.id.cc_scrim);
        ccTime         = ccRootView.findViewById(R.id.cc_time);
        ccDate         = ccRootView.findViewById(R.id.cc_date);
        ccBattery      = ccRootView.findViewById(R.id.cc_battery);
        ccWifiIcon     = ccRootView.findViewById(R.id.cc_wifi_icon);
        ccBtIcon       = ccRootView.findViewById(R.id.cc_bt_icon);
        ccWifiImg      = ccRootView.findViewById(R.id.cc_wifi_img);
        ccBtImg        = ccRootView.findViewById(R.id.cc_bt_img);
        seekBrightness = ccRootView.findViewById(R.id.cc_seek_brightness);
        seekVolume     = ccRootView.findViewById(R.id.cc_seek_volume);

        // 卡片容器
        wifiCard = ccRootView.findViewById(R.id.cc_wifi_card);
        btCard   = ccRootView.findViewById(R.id.cc_bt_card);
        // 中间区域滑动检测层
        swipeArea = ccRootView.findViewById(R.id.cc_swipe_area);

        // 蒙层不可点击（只能通过滑动关闭）
        ccScrim.setClickable(false);
        ccScrim.setFocusable(false);

        // 中间区域向左滑动关闭控制中心
        setupSwipeToClose();

        // WiFi 卡片点击
        wifiCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleWifi();
            }
        });
        // WiFi 卡片长按 - 进入WiFi设置
        wifiCard.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openWifiSettings();
                return true;
            }
        });

        // 蓝牙卡片点击
        btCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBluetooth();
            }
        });
        // 蓝牙卡片长按 - 进入蓝牙设置
        btCard.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openBluetoothSettings();
                return true;
            }
        });

        // 亮度 SeekBar（0-100 百分比）
        seekBrightness.setMax(100);
        seekBrightness.setProgress(getCurrentBrightnessPercent());
        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    setBrightnessPercent(p);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // 音量 SeekBar（0-100 百分比）
        seekVolume.setMax(100);
        seekVolume.setProgress(getCurrentVolumePercent());
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    setVolumePercent(p);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });



        // 初始化 WiFi 状态广播接收器
        setupWifiStateReceiver();
        // 初始化蓝牙状态广播接收器
        setupBtStateReceiver();
    }

    private void setupWifiStateReceiver() {
        wifiStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                    // WiFi开关状态变化
                    refreshIcons();
                } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    // WiFi连接状态变化
                    refreshIcons();
                }
            }
        };
    }

    private void registerWifiReceiver() {
        if (wifiStateReceiver != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            activity.registerReceiver(wifiStateReceiver, filter);
        }
    }

    private void unregisterWifiReceiver() {
        if (wifiStateReceiver != null) {
            try {
                activity.unregisterReceiver(wifiStateReceiver);
            } catch (Exception ignored) {}
        }
    }

    private void setupBtStateReceiver() {
        btStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    // 蓝牙开关状态变化
                    refreshIcons();
                }
            }
        };
    }

    private void registerBtReceiver() {
        if (btStateReceiver != null) {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            activity.registerReceiver(btStateReceiver, filter);
        }
    }

    private void unregisterBtReceiver() {
        if (btStateReceiver != null) {
            try {
                activity.unregisterReceiver(btStateReceiver);
            } catch (Exception ignored) {}
        }
    }

    private void openWifiSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            activity.startActivity(intent);
            hide(); // 打开设置后关闭控制中心
        } catch (Exception e) {
            Toast.makeText(activity, "无法打开WiFi设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBluetoothSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            activity.startActivity(intent);
            hide(); // 打开设置后关闭控制中心
        } catch (Exception e) {
            Toast.makeText(activity, "无法打开蓝牙设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSwipeToClose() {
        if (swipeArea == null) return;
        swipeArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float threshold = SWIPE_THRESHOLD_DP * activity.getResources().getDisplayMetrics().density;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        swipeStartX = event.getRawX();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        // 不拦截，让子视图处理点击
                        return false;
                    case MotionEvent.ACTION_UP:
                        if (swipeStartX >= 0) {
                            float dx = swipeStartX - event.getRawX(); // 向左为正
                            if (dx > threshold) {
                                hide();
                                swipeStartX = -1;
                                return true;
                            }
                        }
                        swipeStartX = -1;
                        return false;
                    case MotionEvent.ACTION_CANCEL:
                        swipeStartX = -1;
                        return false;
                }
                return false;
            }
        });
    }

    private void toggleWifi() {
        try {
            if (wifiManager != null) {
                boolean newState = !wifiManager.isWifiEnabled();
                wifiManager.setWifiEnabled(newState);
                updateWifiIcon(newState);
                updateStatusIcons();
            }
        } catch (Exception e) {
            Toast.makeText(activity, "无法切换WiFi", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleBluetooth() {
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null) {
                boolean newState = !bt.isEnabled();
                if (newState) bt.enable();
                else bt.disable();
                updateBtIcon(newState);
                updateStatusIcons();
            }
        } catch (Exception e) {
            Toast.makeText(activity, "无法切换蓝牙", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateWifiIcon(boolean enabled) {
        if (enabled) {
            ccWifiImg.setBackgroundResource(R.drawable.cc_icon_bg_active);
            ccWifiImg.setColorFilter(0xFFFFFFFF); // 白色图标
        } else {
            ccWifiImg.setBackgroundResource(R.drawable.cc_icon_bg_inactive);
            ccWifiImg.setColorFilter(0xFF888888); // 灰色图标
        }
    }

    private void updateBtIcon(boolean enabled) {
        if (enabled) {
            ccBtImg.setBackgroundResource(R.drawable.cc_icon_bg_active);
            ccBtImg.setColorFilter(0xFFFFFFFF); // 白色图标
        } else {
            ccBtImg.setBackgroundResource(R.drawable.cc_icon_bg_inactive);
            ccBtImg.setColorFilter(0xFF888888); // 灰色图标
        }
    }

    public void show() {
        if (!ccEnabled || isShowing) return;
        isShowing = true;

        refreshInfo();
        refreshIcons();

        // 刷新音量（百分比）
        int volPercent = getCurrentVolumePercent();
        seekVolume.setProgress(volPercent);

        // 刷新亮度（百分比）
        int brightPercent = getCurrentBrightnessPercent();
        seekBrightness.setProgress(brightPercent);

        // 显示根视图
        ccRootView.setVisibility(View.VISIBLE);

        // 面板从左侧滑入动画（从左向右）
        TranslateAnimation anim = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, -1f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f);
        anim.setDuration(260);
        anim.setFillAfter(true);
        ccPanel.startAnimation(anim);

        // 蒙层淡入
        ccScrim.setAlpha(0f);
        ccScrim.animate().alpha(1f).setDuration(260).setListener(null);

        // 注册 WiFi 状态广播接收器
        registerWifiReceiver();
        // 注册蓝牙状态广播接收器
        registerBtReceiver();

        startTimeUpdate();
    }

    public void hide() {
        if (!isShowing) return;

        // 面板向左滑出
        TranslateAnimation anim = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, -1f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f);
        anim.setDuration(200);
        anim.setFillAfter(true);
        ccPanel.startAnimation(anim);

        ccScrim.animate().alpha(0f).setDuration(200).setListener(null);

        // 注销 WiFi 状态广播接收器
        unregisterWifiReceiver();
        // 注销蓝牙状态广播接收器
        unregisterBtReceiver();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ccPanel.clearAnimation();
                ccPanel.setTranslationX(0f);
                ccRootView.setVisibility(View.GONE);
                isShowing = false;
                stopTimeUpdate();
            }
        }, 210);
    }

    public boolean isShowing() {
        return isShowing;
    }

    /**
     * 检查是否已连接到WiFi网络（不只是WiFi开关打开）
     */
    private boolean isWifiConnected() {
        if (wifiManager == null) return false;
        if (!wifiManager.isWifiEnabled()) return false;
        
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) return false;
        
        // 检查是否已连接到某个网络（networkId != -1）
        return wifiInfo.getNetworkId() != -1;
    }

    private void refreshIcons() {
        // 按钮图标：只要WiFi开关打开就显示开启
        boolean wifiEnabled = wifiManager != null && wifiManager.isWifiEnabled();
        updateWifiIcon(wifiEnabled);

        boolean btOn = false;
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            btOn = bt != null && bt.isEnabled();
        } catch (Exception ignored) {}
        updateBtIcon(btOn);

        updateStatusIcons();
    }

    private void updateStatusIcons() {
        // 时间屏WiFi图标：连接后才显示（保持不变）
        boolean wifiConnected = isWifiConnected();
        ccWifiIcon.setVisibility(wifiConnected ? View.VISIBLE : View.GONE);
        
        // 时间屏蓝牙图标：只要打开蓝牙开关就显示
        boolean btOn = false;
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            btOn = bt != null && bt.isEnabled();
        } catch (Exception ignored) {}
        ccBtIcon.setVisibility(btOn ? View.VISIBLE : View.GONE);
    }

    private void refreshInfo() {
        Date now = new Date();
        ccTime.setText(formatTime(now));
        ccDate.setText(formatDate(now));
        updateBattery();
    }

    private void updateBattery() {
        Intent batteryIntent = activity.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level  = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale  = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (level >= 0 && scale > 0) {
                int pct = (int) ((level / (float) scale) * 100);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                ccBattery.setText(charging ? "⚡ " + pct + "%" : pct + "%");
            }
        }
    }

    private String formatTime(Date date) {
        String pattern;
        if (is24HourFormat) {
            pattern = showSeconds ? "HH:mm:ss" : "HH:mm";
        } else {
            pattern = showSeconds ? "hh:mm:ss a" : "hh:mm a";
        }
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA).format(date);
    }

    // ===== 音量百分比控制 =====
    private int getCurrentVolumePercent() {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        return maxVolume > 0 ? (current * 100 / maxVolume) : 0;
    }

    private void setVolumePercent(int percent) {
        int volume = maxVolume * percent / 100;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
    }

    // ===== 亮度百分比控制 =====
    private int getCurrentBrightnessPercent() {
        try {
            int value = Settings.System.getInt(activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 128);
            return value * 100 / 255;
        } catch (Exception e) {
            return 50;
        }
    }

    private void setBrightnessPercent(int percent) {
        int value = percent * 255 / 100;
        try {
            Settings.System.putInt(activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, value);
            android.view.WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            lp.screenBrightness = percent / 100f;
            activity.getWindow().setAttributes(lp);
        } catch (Exception e) {
            // 无权限时仅改变当前窗口亮度
            android.view.WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            lp.screenBrightness = percent / 100f;
            activity.getWindow().setAttributes(lp);
        }
    }

    private void startTimeUpdate() {
        stopTimeUpdate();
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                if (isShowing) {
                    refreshInfo();
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(timeRunnable);
    }

    private void stopTimeUpdate() {
        if (timeRunnable != null) {
            handler.removeCallbacks(timeRunnable);
            timeRunnable = null;
        }
    }

    public void showCcSettingsDialog() {
        View dialogView = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_cc_settings, null);
        SwitchCompat switchEnable = dialogView.findViewById(R.id.switch_cc_enable);
        RadioGroup   radioGroup   = dialogView.findViewById(R.id.radio_time_format);
        RadioButton  radio24h     = dialogView.findViewById(R.id.radio_24h);
        RadioButton  radio12h     = dialogView.findViewById(R.id.radio_12h);
        SwitchCompat switchSecs   = dialogView.findViewById(R.id.switch_show_seconds);

        switchEnable.setChecked(ccEnabled);
        if (is24HourFormat) radio24h.setChecked(true);
        else radio12h.setChecked(true);
        switchSecs.setChecked(showSeconds);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("控制中心设置")
                .setView(dialogView)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "确定",
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        ccEnabled = switchEnable.isChecked();
                        is24HourFormat = radio24h.isChecked();
                        showSeconds = switchSecs.isChecked();
                        saveSettings();
                    }
                });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消",
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }

    public void destroy() {
        stopTimeUpdate();
        if (ccRootView != null) {
            try {
                ((ViewGroup) ccRootView.getParent()).removeView(ccRootView);
            } catch (Exception ignored) {}
        }
    }
}
