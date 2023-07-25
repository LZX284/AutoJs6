package org.autojs.autojs.runtime.api;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.util.DeviceUtils;
import org.autojs.autojs6.inrt.R;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import ezy.assist.compat.SettingsCompat;

/**
 * Created by Stardust on 2017/12/2.
 */
public class Device {

    private Intent mBatteryChangedActionIntent;

    private static int getWidth() {
        return ScreenMetrics.getDeviceScreenWidth();
    }

    private static int getHeight() {
        return ScreenMetrics.getDeviceScreenHeight();
    }

    public static final String buildId = Build.DISPLAY;

    public static final String buildDisplay = Build.DISPLAY;

    public static final String product = Build.PRODUCT;

    public static final String board = Build.BOARD;

    public static final String brand = Build.BRAND;

    public static final String manufacturer = Build.MANUFACTURER;

    public static final String device = Build.DEVICE;

    public static final String model = Build.MODEL;

    public static final String bootloader = Build.BOOTLOADER;

    public static final String hardware = Build.HARDWARE;

    public static final String fingerprint = Build.FINGERPRINT;

    public static final int sdkInt = Build.VERSION.SDK_INT;

    public static final String incremental = Build.VERSION.INCREMENTAL;

    public static final String release = Build.VERSION.RELEASE;

    public static final String baseOS = Build.VERSION.BASE_OS;

    public static final String securityPatch = Build.VERSION.SECURITY_PATCH;

    public static final String codename = Build.VERSION.CODENAME;

    public static String serial;

    public static String imei;

    private final Context mContext;
    private final Vibrator mVibrator;
    private PowerManager.WakeLock mWakeLock;
    private int mWakeLockFlag;

    public Device(Context context) {
        mContext = context;
        mVibrator = context.getSystemService(Vibrator.class);
        imei = DeviceUtils.getIMEI(context);
        serial = DeviceUtils.getSerial();
    }

    @Nullable
    public String getIMEI() {
        return imei;
    }

    @Nullable
    public String getSerial() {
        return serial;
    }

    @SuppressLint("HardwareIds")
    public String getAndroidId() {
        return Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public int getBrightness() throws Settings.SettingNotFoundException {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
    }

    public int getBrightnessMode() throws Settings.SettingNotFoundException {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
    }

    public void setBrightness(int b) throws Settings.SettingNotFoundException {
        checkWriteSettingsPermission();
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, b);
    }

    public void setBrightnessMode(int b) throws Settings.SettingNotFoundException {
        checkWriteSettingsPermission();
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, b);
    }

    public int getMusicVolume() {
        return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public int getNotificationVolume() {
        return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .getStreamVolume(AudioManager.STREAM_NOTIFICATION);
    }

    public int getAlarmVolume() {
        return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .getStreamVolume(AudioManager.STREAM_ALARM);
    }

    public int getMusicMaxVolume() {
        return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public int getNotificationMaxVolume() {
        return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
    }

    public int getAlarmMaxVolume() {
        return ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .getStreamMaxVolume(AudioManager.STREAM_ALARM);
    }

    public void setMusicVolume(int i) {
        checkWriteSettingsPermission();
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setStreamVolume(AudioManager.STREAM_MUSIC, i, 0);
    }

    public void setAlarmVolume(int i) {
        checkWriteSettingsPermission();
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setStreamVolume(AudioManager.STREAM_ALARM, i, 0);
    }

    public void setNotificationVolume(int i) {
        checkWriteSettingsPermission();
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setStreamVolume(AudioManager.STREAM_NOTIFICATION, i, 0);
    }

    public float getBattery() {
        Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null) {
            return -1;
        }
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float battery = ((float) level / scale) * 100f;
        return Math.round(battery * 10) / 10f;
    }

    public long getTotalMem() {
        ActivityManager activityManager = getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(info);
        return info.totalMem;
    }

    public long getAvailMem() {
        ActivityManager activityManager = getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(info);
        return info.availMem;
    }

    public boolean isCharging() {
        return DeviceUtils.isCharging(mContext);
    }

    public boolean isPowerSourceAC() {
        return DeviceUtils.isPowerSourceAC(mContext);
    }

    public boolean isPowerSourceUSB() {
        return DeviceUtils.isPowerSourceUSB(mContext);
    }

    public boolean isPowerSourceWireless() {
        return DeviceUtils.isPowerSourceWireless(mContext);
    }

    public boolean isPowerSourceDock() {
        return DeviceUtils.isPowerSourceDock(mContext);
    }

    public void keepAwake(int flags, long timeout) {
        checkWakeLock(flags);
        mWakeLock.acquire(timeout);
    }

    @SuppressLint("WakelockTimeout")
    public void keepAwake(int flags) {
        checkWakeLock(flags);
        mWakeLock.acquire();
    }

    public boolean isScreenOn() {
        return ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getState() == Display.STATE_ON;
    }

    public void wakeUpIfNeeded() {
        if (!isScreenOn()) {
            wakeUp();
        }
    }

    public void wakeUp() {
        keepScreenOn(200);
    }

    public void keepScreenOn() {
        keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP);
    }

    public void keepScreenOn(long timeout) {
        keepAwake(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, timeout);
    }

    public void keepScreenDim() {
        keepAwake(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP);
    }

    public void keepScreenDim(long timeout) {
        keepAwake(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, timeout);
    }

    private void checkWakeLock(int flags) {
        if (mWakeLock == null || flags != mWakeLockFlag) {
            mWakeLockFlag = flags;
            cancelKeepingAwake();
            mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(flags, Device.class.getName());
        }
    }

    public void cancelKeepingAwake() {
        if (mWakeLock != null && mWakeLock.isHeld())
            mWakeLock.release();
    }

    public void vibrate(long millis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            mVibrator.vibrate(millis);
        }
    }

    public void vibrate(long off, long millis) {
        vibrate(new long[]{off, millis});
    }

    public void vibrate(long[] timings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createWaveform(timings, -1));
        } else {
            mVibrator.vibrate(timings, -1);
        }
    }

    public void cancelVibration() {
        mVibrator.cancel();
    }

    private void checkWriteSettingsPermission() {
        if (SettingsCompat.canWriteSettings(mContext)) {
            return;
        }
        SettingsCompat.manageWriteSettings(mContext);
        throw new SecurityException(mContext.getString(R.string.error_no_write_settings_permission));
    }

    private void checkReadPhoneStatePermission() {
        if (mContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(mContext.getString(R.string.error_no_read_phone_state_permission));
        }
    }

    // just to avoid warning of null pointer to make android studio happy..
    @NonNull
    @SuppressWarnings("unchecked")
    private <T> T getSystemService(String service) {
        Object systemService = mContext.getSystemService(service);
        if (systemService == null) {
            throw new RuntimeException("Should never happen... " + service);
        }
        return (T) systemService;
    }

    private static final String FAKE_MAC_ADDRESS = "02:00:00:00:00:00";

    @SuppressLint("HardwareIds")
    public String getMacAddress() throws Exception {
        WifiManager wifiMan = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiMan == null) {
            return null;
        }
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        if (wifiInf == null) {
            return getMacByFile();
        }

        @SuppressLint("MissingPermission")
        String mac = wifiInf.getMacAddress();

        if (FAKE_MAC_ADDRESS.equals(mac)) {
            mac = null;
        }
        if (mac == null) {
            mac = getMacByInterface();
            if (mac == null) {
                mac = getMacByFile();
            }
        }
        return mac;
    }

    private static String getMacByInterface() throws SocketException {
        List<NetworkInterface> networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface networkInterface : networkInterfaces) {
            if (networkInterface.getName().equalsIgnoreCase("wlan0")) {
                byte[] macBytes = networkInterface.getHardwareAddress();
                if (macBytes == null) {
                    return null;
                }

                StringBuilder mac = new StringBuilder();
                for (byte b : macBytes) {
                    mac.append(String.format("%02X:", b));
                }

                if (mac.length() > 0) {
                    mac.deleteCharAt(mac.length() - 1);
                }
                return mac.toString();
            }
        }
        return null;
    }

    private static String getMacByFile() {
        try {
            return PFiles.read("/sys/class/net/wlan0/address");
        } catch (UncheckedIOException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Device{" +
                "width=" + getWidth() +
                ", height=" + getHeight() +
                ", buildId='" + buildId + '\'' +
                ", buildDisplay='" + buildDisplay + '\'' +
                ", product='" + product + '\'' +
                ", board='" + board + '\'' +
                ", brand='" + brand + '\'' +
                ", device='" + device + '\'' +
                ", model='" + model + '\'' +
                ", bootloader='" + bootloader + '\'' +
                ", hardware='" + hardware + '\'' +
                ", fingerprint='" + fingerprint + '\'' +
                ", sdkInt=" + sdkInt +
                ", incremental='" + incremental + '\'' +
                ", release='" + release + '\'' +
                ", baseOS='" + baseOS + '\'' +
                ", securityPatch='" + securityPatch + '\'' +
                ", serial='" + serial + '\'' +
                '}';
    }

}
