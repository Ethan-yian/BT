package com.example.app300;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app300.BTserver.BluetoothService;
import com.example.app300.activity.MainActivity;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
    private final Context context;
    private final List<BluetoothDevice> devices;
    private final BluetoothService bluetoothService;

    public DeviceAdapter(Context context, List<BluetoothDevice> devices, BluetoothService bluetoothService) {
        this.context = context;
        this.devices = devices;
        this.bluetoothService = bluetoothService;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.device_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);

        try {
            if (checkBluetoothPermissions()) {
                // 设置设备名称和地址
                String deviceName = device.getName();
                holder.deviceName.setText(deviceName != null ? deviceName : "未知设备");
                holder.deviceAddress.setText(device.getAddress());

                // 更新按钮状态
                updateButtonState(holder, device);

                // 设置连接按钮点击事件
                holder.connectButton.setOnClickListener(v -> handleDeviceConnection(device));
            }
        } catch (SecurityException e) {
            handleSecurityException("获取设备信息时发生错误");
        }
    }

    private boolean checkBluetoothPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void handleSecurityException(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private void updateButtonState(ViewHolder holder, BluetoothDevice device) {
        boolean isThisDeviceConnected = isDeviceConnected(device);
        boolean isAnyDeviceConnected = bluetoothService.isConnected();
        boolean isConnecting = bluetoothService.isConnecting();

        // 设置按钮状态
        if (isThisDeviceConnected) {
            // 当前设备已连接
            holder.connectButton.setText("断开");
            holder.connectButton.setBackgroundColor(ContextCompat.getColor(context,
                    android.R.color.holo_red_dark));
            holder.connectButton.setEnabled(true);
        } else if (isConnecting) {
            // 有设备正在连接中
            holder.connectButton.setText("连接中");
            holder.connectButton.setBackgroundColor(ContextCompat.getColor(context,
                    android.R.color.darker_gray));
            holder.connectButton.setEnabled(false);
        } else if (isAnyDeviceConnected) {
            // 其他设备已连接
            holder.connectButton.setText("连接");
            holder.connectButton.setBackgroundColor(ContextCompat.getColor(context,
                    android.R.color.darker_gray));
            holder.connectButton.setEnabled(false);
        } else {
            // 没有设备连接
            holder.connectButton.setText("连接");
            holder.connectButton.setBackgroundColor(ContextCompat.getColor(context,
                    android.R.color.holo_blue_dark));
            holder.connectButton.setEnabled(true);
        }
    }

    private boolean isDeviceConnected(BluetoothDevice device) {
        BluetoothDevice connectedDevice = bluetoothService.getConnectedDevice();
        return connectedDevice != null &&
                connectedDevice.getAddress().equals(device.getAddress());
    }

    private void handleDeviceConnection(BluetoothDevice device) {
        if (!checkBluetoothPermissions()) {
            handleSecurityException("需要蓝牙权限");
            return;
        }

        try {
            if (isDeviceConnected(device)) {
                // 断开当前设备
                bluetoothService.disconnect();
                notifyDataSetChanged();
            } else if (!bluetoothService.isConnected() && !bluetoothService.isConnecting()) {
                // 只有在没有设备连接且不在连接过程中时才允许新的连接
                boolean startedConnection = bluetoothService.connect(device);
                if (!startedConnection) {
                    Toast.makeText(context, "无法启动连接，请检查蓝牙状态", Toast.LENGTH_SHORT).show();
                }
                notifyDataSetChanged();
            } else {
                // 其他设备已连接或正在连接中
                Toast.makeText(context, "请先断开当前连接的设备", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            handleSecurityException("连接设备时发生错误");
        }
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        Button connectButton;

        ViewHolder(View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceAddress = itemView.findViewById(R.id.deviceAddress);
            connectButton = itemView.findViewById(R.id.connectButton);
        }
    }

    /**
     * 更新所有设备的连接状态显示
     */
    public void updateAllDevicesState() {
        notifyDataSetChanged();
    }

    /**
     * 更新指定设备的连接状态
     * @param device 需要更新状态的设备
     * @param connected 连接状态
     */
    public void updateDeviceState(BluetoothDevice device, boolean connected) {
        int position = getDevicePosition(device);
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    private int getDevicePosition(BluetoothDevice device) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getAddress().equals(device.getAddress())) {
                return i;
            }
        }
        return -1;
    }
}