package com.example.app300.BTserver;  // 确保这是您的正确包名

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;  // 新添加的导入
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothService {
    private static final String TAG = "BluetoothService";
    private static BluetoothService instance;
    private static final long CONNECTION_TIMEOUT = 10000; // 10秒连接超时

    // BLE服务和特征的UUID
    private static final UUID SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothDevice connectedDevice;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private DataReceiveListener dataReceiveListener;
    private final List<ConnectionStateChangeListener> connectionListeners = new ArrayList<>();
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    public interface DataReceiveListener {
        void onDataReceived(String data);
    }

    public interface ConnectionStateChangeListener {
        void onConnectionStateChanged(boolean connected);
    }

    protected BluetoothService(Context context) {
        this.context = context.getApplicationContext();
        this.timeoutHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized BluetoothService getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothService(context.getApplicationContext());
        }
        return instance;
    }

    public void addConnectionStateChangeListener(ConnectionStateChangeListener listener) {
        if (listener != null && !connectionListeners.contains(listener)) {
            connectionListeners.add(listener);
            // 立即通知当前状态
            new Handler(Looper.getMainLooper()).post(() ->
                    listener.onConnectionStateChanged(isConnected));
        }
    }

    public void removeConnectionStateChangeListener(ConnectionStateChangeListener listener) {
        connectionListeners.remove(listener);
    }

    private void notifyConnectionStateChange(boolean connected) {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (ConnectionStateChangeListener listener : new ArrayList<>(connectionListeners)) {
                if (listener != null) {
                    listener.onConnectionStateChanged(connected);
                }
            }
        });
    }

    private boolean checkBluetoothPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean connect(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "无法连接：设备对象为空");
            return false;
        }

        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "缺少必需的蓝牙权限");
            return false;
        }

        if (isConnected || isConnecting) {
            Log.w(TAG, "已有设备连接或正在连接中");
            return false;
        }

        try {
            // 清理现有连接
            disconnect();

            // 开始新的连接
            Log.i(TAG, "开始连接设备: " + device.getAddress());
            isConnecting = true;
            connectedDevice = device;

            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);

            if (bluetoothGatt == null) {
                Log.e(TAG, "创建GATT连接失败");
                cleanup();
                return false;
            }

            // 设置连接超时
            timeoutRunnable = () -> {
                if (isConnecting && !isConnected) {
                    Log.w(TAG, "连接超时");
                    cleanup();
                }
            };
            timeoutHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT);

            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "连接时发生安全异常: " + e.getMessage());
            cleanup();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "连接时发生异常: " + e.getMessage());
            cleanup();
            return false;
        }
    }

    public void disconnect() {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "缺少必需的蓝牙权限");
            return;
        }

        try {
            if (timeoutRunnable != null) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                timeoutRunnable = null;
            }

            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
            }

            cleanup();
        } catch (SecurityException e) {
            Log.e(TAG, "断开连接时发生安全异常: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "断开连接时发生异常: " + e.getMessage());
        }
    }

    public void cleanup() {
        if (bluetoothGatt != null) {
            try {
                if (checkBluetoothPermissions()) {
                    bluetoothGatt.close();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security Exception during cleanup: " + e.getMessage());
            }
            bluetoothGatt = null;
        }
        isConnecting = false;
        isConnected = false;
        connectedDevice = null;
        writeCharacteristic = null;
        notifyConnectionStateChange(false);
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public BluetoothDevice getConnectedDevice() {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "缺少必需的蓝牙权限");
            return null;
        }
        return connectedDevice;
    }

    public void setDataReceiveListener(DataReceiveListener listener) {
        this.dataReceiveListener = listener;
    }

    private boolean isNotificationEnabled(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null) {
            Log.e(TAG, "无法检查通知状态：GATT或特征值为空");
            return false;
        }

        try {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));

            if (descriptor != null) {
                byte[] value = descriptor.getValue();
                boolean enabled = (value != null &&
                        value.length == 2 &&
                        value[0] == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[0] &&
                        value[1] == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[1]);

                Log.d(TAG, "特征值 " + characteristic.getUuid() + " 的通知状态: " +
                        (enabled ? "已启用" : "未启用"));

                return enabled;
            } else {
                Log.e(TAG, "特征值 " + characteristic.getUuid() + " 没有通知描述符");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "检查通知状态时发生异常: " + e.getMessage());
            return false;
        }
    }

    public boolean sendMessage(String message) {
        if (!checkBluetoothPermissions() || !isConnected) {
            Log.e(TAG, "无法发送消息：未连接或无权限");
            return false;
        }

        try {
            if (writeCharacteristic != null && bluetoothGatt != null) {
                // 添加连接状态检查的详细日志
                Log.d(TAG, "准备发送消息，当前连接状态: " +
                        (isConnected ? "已连接" : "未连接") +
                        ", GATT状态: " + (bluetoothGatt != null ? "有效" : "无效"));

                byte[] messageBytes = message.getBytes();
                writeCharacteristic.setValue(messageBytes);

                // 添加详细的发送日志
                Log.d(TAG, "正在发送消息: '" + message + "', 字节长度: " + messageBytes.length);

                // 写入特征值并记录结果
                boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);

                if (success) {
                    Log.d(TAG, "消息发送请求成功");
                } else {
                    Log.e(TAG, "消息发送请求失败");
                }

                return success;
            } else {
                Log.e(TAG, "无法发送消息: " +
                        (writeCharacteristic == null ? "特征值未初始化" : "GATT连接无效"));
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "发送消息时发生安全异常: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "发送消息时发生异常: " + e.getMessage());
            return false;
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (!checkBluetoothPermissions()) {
                Log.e(TAG, "缺少必需的蓝牙权限");
                cleanup();
                return;
            }

            try {
                if (timeoutRunnable != null) {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "设备连接成功");
                        isConnecting = false;
                        isConnected = true;
                        gatt.discoverServices();
                        notifyConnectionStateChange(true);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "设备已断开连接");
                        cleanup();
                    }
                } else {
                    Log.e(TAG, "连接状态改变，但状态码错误: " + status);
                    cleanup();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "处理连接状态变化时发生安全异常: " + e.getMessage());
                cleanup();
            } catch (Exception e) {
                Log.e(TAG, "处理连接状态变化时发生异常: " + e.getMessage());
                cleanup();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (!checkBluetoothPermissions()) {
                Log.e(TAG, "缺少必需的蓝牙权限");
                cleanup();
                return;
            }

            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                        if (writeCharacteristic != null) {
                            // 首先启用通知
                            boolean enableNotification = gatt.setCharacteristicNotification(writeCharacteristic, true);

                            if (enableNotification) {
                                // 获取通知描述符
                                BluetoothGattDescriptor descriptor = writeCharacteristic.getDescriptor(
                                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));

                                if (descriptor != null) {
                                    // 设置描述符值以启用通知
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    boolean writeSuccess = gatt.writeDescriptor(descriptor);
                                    Log.i(TAG, "通知描述符写入" + (writeSuccess ? "成功" : "失败"));
                                } else {
                                    Log.e(TAG, "未找到通知描述符");
                                }
                                Log.i(TAG, "服务和特征配置成功");
                            } else {
                                Log.e(TAG, "启用通知失败");
                                cleanup();
                            }
                        } else {
                            Log.e(TAG, "未找到所需特征");
                            cleanup();
                        }
                    } else {
                        Log.e(TAG, "未找到所需服务");
                        cleanup();
                    }
                } else {
                    Log.e(TAG, "服务发现失败: " + status);
                    cleanup();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "服务发现过程中发生安全异常: " + e.getMessage());
                cleanup();
            } catch (Exception e) {
                Log.e(TAG, "服务发现过程中发生异常: " + e.getMessage());
                cleanup();
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (!checkBluetoothPermissions()) {
                Log.e(TAG, "缺少必需的蓝牙权限");
                return;
            }

            try {
                if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                    final byte[] data = characteristic.getValue();
                    if (data != null && dataReceiveListener != null) {
                        final String receivedData = new String(data);
                        // 添加详细的数据接收日志
                        Log.d(TAG, "收到原始蓝牙数据: " + receivedData);
                        Log.d(TAG, "数据长度: " + data.length + " 字节");

                        // 尝试解析数据为整数（用于调试）
                        try {
                            int value = Integer.parseInt(receivedData.trim());
                            Log.d(TAG, "解析后的整数值: " + value);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "数据无法解析为整数: " + receivedData);
                        }

                        // 在主线程中处理数据
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Log.d(TAG, "正在主线程中调用监听器处理数据: " + receivedData);
                            if (dataReceiveListener != null) {
                                dataReceiveListener.onDataReceived(receivedData);
                                Log.d(TAG, "数据已传递给监听器");
                            } else {
                                Log.w(TAG, "数据接收监听器为null");
                            }
                        });
                    } else {
                        Log.w(TAG, "收到空数据或没有设置监听器");
                    }
                } else {
                    Log.d(TAG, "收到来自其他特征的数据: " + characteristic.getUuid());
                }
            } catch (SecurityException e) {
                Log.e(TAG, "处理特征值变化时发生安全异常: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "处理特征值变化时发生异常: " + e.getMessage());
            }
        }
    };
}