package com.example.app300.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.app300.BTserver.BluetoothService;
import com.example.app300.DeviceAdapter;
import com.example.app300.R;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BluetoothService.ConnectionStateChangeListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_CODE = 2;
    private static final long SCAN_PERIOD = 10000; // 扫描持续时间10秒
    private static final int REQUEST_CONTROL_ACTIVITY = 3;

    // 必需的权限列表
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // 成员变量
    private Button editModeButton;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothService bluetoothService;
    private Handler handler;
    private boolean isScanning = false;
    private boolean isActivityActive = false;

    private DeviceAdapter deviceAdapter;
    private List<BluetoothDevice> deviceList;
    private Button gamepadButton;
    private Button refreshButton;
    private RecyclerView deviceListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isActivityActive = true;
        handler = new Handler(Looper.getMainLooper());

        try {
            initializeVariables();
            initializeViews();
            checkAndRequestPermissions();
        } catch (Exception e) {
            Log.e(TAG, "初始化失败: " + e.getMessage());
            showError("初始化失败，请检查设备设置");
        }
    }

    private void initializeVariables() {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter != null) {
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                }
            }
            bluetoothService = BluetoothService.getInstance(this);
            deviceList = new ArrayList<>();
        } catch (SecurityException e) {
            Log.e(TAG, "初始化蓝牙变量失败: " + e.getMessage());
            throw e;
        }
    }

    private void initializeViews() {
        try {
            // 初始化RecyclerView
            deviceListView = findViewById(R.id.deviceList);
            deviceAdapter = new DeviceAdapter(this, deviceList, bluetoothService);
            deviceListView.setLayoutManager(new LinearLayoutManager(this));
            deviceListView.setAdapter(deviceAdapter);

            // 初始化按钮
            editModeButton = findViewById(R.id.editModeButton);
            gamepadButton = findViewById(R.id.gamepadButton);
            refreshButton = findViewById(R.id.refreshButton);

            // 设置按钮初始状态
            editModeButton.setEnabled(true); // 编辑模式按钮始终可用
            gamepadButton.setEnabled(false); // 手柄模式按钮初始禁用，需要蓝牙连接

            // 设置按钮点击事件
            editModeButton.setOnClickListener(v -> {
                // 编辑模式不需要检查蓝牙连接
                startControlActivity(true, false);
            });

            gamepadButton.setOnClickListener(v -> {
                if (checkBluetoothConnectionState()) {
                    startControlActivity(false, true);
                }
            });

            refreshButton.setOnClickListener(v -> {
                if (checkBluetoothPermissions()) {
                    stopScanning();
                    startScan();
                    showToast("正在刷新设备列表...");
                } else {
                    showPermissionError();
                }
            });

            updateUIState(); // 确保UI状态与当前蓝牙状态一致
        } catch (Exception e) {
            Log.e(TAG, "初始化视图失败: " + e.getMessage());
            throw e;
        }
    }

    private boolean checkBluetoothConnectionState() {
        if (!bluetoothService.isConnected()) {
            showError("请先连接蓝牙设备");
            return false;
        }
        return true;
    }

    @SuppressLint("InlinedApi")
    private boolean checkBluetoothPermissions() {
        try {
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "权限检查失败: " + e.getMessage());
            return false;
        }
    }

    private void checkAndRequestPermissions() {
        if (!checkBluetoothPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        } else {
            initializeBluetooth();
        }
    }

    private void initializeBluetooth() {
        try {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (checkBluetoothPermissions()) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } else {
                startScan();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "初始化蓝牙失败: " + e.getMessage());
            handleSecurityException(e, "初始化蓝牙");
        }
    }

    private void startScan() {
        if (!isScanning && bluetoothLeScanner != null) {
            try {
                if (!checkBluetoothPermissions()) {
                    showPermissionError();
                    return;
                }

                refreshButton.setEnabled(false);

                // 设置扫描超时
                handler.postDelayed(this::stopScanning, SCAN_PERIOD);

                deviceList.clear();
                deviceAdapter.notifyDataSetChanged();
                isScanning = true;
                bluetoothLeScanner.startScan(scanCallback);
            } catch (SecurityException e) {
                Log.e(TAG, "开始扫描失败: " + e.getMessage());
                handleSecurityException(e, "开始扫描");
                isScanning = false;
                refreshButton.setEnabled(true);
            }
        }
    }

    private void stopScanning() {
        try {
            if (isScanning && checkBluetoothPermissions()) {
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                refreshButton.setEnabled(true);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "停止扫描失败: " + e.getMessage());
            handleSecurityException(e, "停止扫描");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            try {
                if (!checkBluetoothPermissions()) return;

                if (isActivityActive) {
                    BluetoothDevice device = result.getDevice();
                    if (!deviceList.contains(device)) {
                        deviceList.add(device);
                        runOnUiThread(() -> deviceAdapter.notifyDataSetChanged());
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "处理扫描结果失败: " + e.getMessage());
                handleSecurityException(e, "处理扫描结果");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "扫描失败. 错误代码: " + errorCode);
            runOnUiThread(() -> {
                showError("扫描失败，错误代码：" + errorCode);
                refreshButton.setEnabled(true);
            });
        }
    };

    private void startControlActivity(boolean editMode, boolean handleMode) {
        if (!isActivityActive || isFinishing()) return;

        Intent intent = new Intent(MainActivity.this, ControlActivity.class);
        intent.putExtra("EDIT_MODE", editMode);
        intent.putExtra("HANDLE_MODE", handleMode);
        startActivityForResult(intent, REQUEST_CONTROL_ACTIVITY);
    }

    private void updateUIState() {
        if (!isActivityActive || isFinishing()) return;

        runOnUiThread(() -> {
            boolean isConnected = bluetoothService.isConnected();

            // 只更新手柄模式按钮状态，编辑模式按钮保持可用
            gamepadButton.setEnabled(isConnected);
            updateButtonAppearance(gamepadButton, isConnected);

            // 更新设备列表状态
            deviceAdapter.updateAllDevicesState();
        });
    }

    private void updateButtonAppearance(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private void handleSecurityException(SecurityException e, String operation) {
        Log.e(TAG, operation + "时发生安全异常: " + e.getMessage());
        showError("执行" + operation + "操作时出错，请检查权限设置");
        checkAndRequestPermissions();
    }

    private void showPermissionError() {
        if (!isActivityActive || isFinishing()) return;

        new AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("此功能需要蓝牙和位置权限才能正常工作。请在设置中授予权限。")
                .setPositiveButton("去设置", (dialog, which) -> checkAndRequestPermissions())
                .setNegativeButton("取消", null)
                .show();
    }

    private void showError(String message) {
        if (!isActivityActive || isFinishing()) return;

        runOnUiThread(() ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void showToast(String message) {
        if (!isActivityActive || isFinishing()) return;

        runOnUiThread(() ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                startScan();
            } else {
                showError("需要启用蓝牙才能使用此功能");
            }
        }
        else if (requestCode == REQUEST_CONTROL_ACTIVITY) {
            if (resultCode == RESULT_OK) {
                updateUIState();
            }
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                startScan();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                initializeBluetooth();
            } else {
                showPermissionExplanationDialog();
            }
        }
    }

    private void showPermissionExplanationDialog() {
        if (!isActivityActive || isFinishing()) return;

        new AlertDialog.Builder(this)
                .setTitle("权限说明")
                .setMessage("没有获得所需权限，部分功能可能无法正常使用。\n\n" +
                        "蓝牙权限：用于扫描和连接设备\n" +
                        "位置权限：用于搜索附近的蓝牙设备\n\n" +
                        "是否重新授权？")
                .setPositiveButton("重新授权", (dialog, which) -> checkAndRequestPermissions())
                .setNegativeButton("暂不授权", null)
                .show();
    }

    @Override
    public void onConnectionStateChanged(boolean connected) {
        if (!isActivityActive || isFinishing()) return;

        runOnUiThread(() -> {
            // 更新UI状态
            updateUIState();

            if (connected) {
                showConnectedSuccessDialog();
            } else {
                showToast("蓝牙连接已断开");
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    startScan();
                }
            }
        });
    }
    private void showConnectedSuccessDialog() {
        if (!isActivityActive || isFinishing()) return;

        try {
            BluetoothDevice device = bluetoothService.getConnectedDevice();
            String deviceName = "未知设备";
            if (device != null && checkBluetoothPermissions()) {
                deviceName = device.getName();
                if (deviceName == null || deviceName.isEmpty()) {
                    deviceName = device.getAddress();
                }
            }

            String finalDeviceName = deviceName;
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("连接成功")
                    .setMessage("已成功连接到设备: " + finalDeviceName)
                    .setPositiveButton("确定", null)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .create();

            // 设置对话框显示和消失的监听
            dialog.setOnShowListener(dialogInterface -> {
                updateUIState(); // 对话框显示时更新UI状态
            });

            dialog.setOnDismissListener(dialogInterface -> {
                updateUIState(); // 对话框消失时更新UI状态
            });

            dialog.show();
        } catch (SecurityException e) {
            Log.e(TAG, "获取设备信息失败: " + e.getMessage());
            showConnectedSuccessDialogSimple();
        }
    }
    private void showConnectedSuccessDialogSimple() {
        if (!isActivityActive || isFinishing()) return;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("连接成功")
                .setMessage("已成功连接到设备")
                .setPositiveButton("确定", null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            updateUIState();
        });

        dialog.setOnDismissListener(dialogInterface -> {
            updateUIState();
        });

        dialog.show();
    }
    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (bluetoothService != null) {
            bluetoothService.addConnectionStateChangeListener(this);
            updateUIState();

            // 如果蓝牙已启用且未在扫描，则开始扫描
            if (!isScanning && bluetoothAdapter != null &&
                    bluetoothAdapter.isEnabled() && checkBluetoothPermissions()) {
                startScan();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (isScanning && checkBluetoothPermissions()) {
                stopScanning();
            }
            if (bluetoothService != null) {
                bluetoothService.removeConnectionStateChangeListener(this);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "暂停活动时发生错误: " + e.getMessage());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            if (isScanning && checkBluetoothPermissions()) {
                stopScanning();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "停止活动时发生错误: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        isActivityActive = false;

        // 清除所有待处理的回调
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        // 停止扫描
        try {
            if (isScanning && checkBluetoothPermissions()) {
                stopScanning();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "销毁活动时停止扫描失败: " + e.getMessage());
        }

        // 移除蓝牙服务监听器
        if (bluetoothService != null) {
            bluetoothService.removeConnectionStateChangeListener(this);
        }

        // 清空设备列表
        if (deviceList != null) {
            deviceList.clear();
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        try {
            if (isScanning && checkBluetoothPermissions()) {
                stopScanning();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "返回时停止扫描失败: " + e.getMessage());
        }

        if (bluetoothService != null && bluetoothService.isConnected()) {
            new AlertDialog.Builder(this)
                    .setTitle("退出确认")
                    .setMessage("是否断开蓝牙连接并退出？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        try {
                            bluetoothService.disconnect();
                            updateUIState(); // 更新UI状态
                            finish();
                        } catch (Exception e) {
                            Log.e(TAG, "断开连接失败: " + e.getMessage());
                            finish();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
    // 用于保存设备列表状态的方法
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isScanning", isScanning);
    }

    // 用于恢复设备列表状态的方法
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        boolean wasScanning = savedInstanceState.getBoolean("isScanning", false);
        if (wasScanning && bluetoothAdapter != null &&
                bluetoothAdapter.isEnabled() && checkBluetoothPermissions()) {
            startScan();
        }
    }

    // 检查蓝牙是否支持和启用
    private boolean checkBluetoothAvailable() {
        if (bluetoothAdapter == null) {
            showError("此设备不支持蓝牙");
            return false;
        }

        try {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (checkBluetoothPermissions()) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "检查蓝牙状态失败: " + e.getMessage());
            handleSecurityException(e, "检查蓝牙状态");
            return false;
        }

        return true;
    }
    // 重试连接的方法
    private void retryConnection() {
        if (!isActivityActive || isFinishing()) return;

        try {
            if (checkBluetoothPermissions() && checkBluetoothAvailable()) {
                stopScanning();
                startScan();
                showToast("正在重新扫描设备...");
            }
        } catch (Exception e) {
            Log.e(TAG, "重试连接失败: " + e.getMessage());
            showError("重试连接失败，请检查蓝牙设置");
        }
    }
}