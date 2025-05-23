package com.example.app300.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.view.Window;
import android.view.Gravity;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import android.content.DialogInterface;
import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.app300.BTserver.BluetoothService;
import com.example.app300.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControlActivity extends AppCompatActivity implements BluetoothService.ConnectionStateChangeListener {
    private static final String TAG = "ControlActivity";
    private static final String PREFS_NAME = "ControlLayout";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String KEY_FIRST_TIME = "isFirstTime";
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    // 成员变量
    private List<View> remainingComponents;  // 存储待添加的组件列表
    private List<View> addedComponents;      // 存储已添加的组件列表
    private Button buttonAdd;
    private BluetoothService bluetoothService;
    private boolean isFirstTimeSetup;
    private boolean isEditMode;
    private Vibrator vibrator;
    private ProgressBar energyBar;
    private SharedPreferences prefs;
    private boolean isLayoutChanged = false;
    private float lastTouchX, lastTouchY;
    private boolean isDragging = false;
    private Map<Integer, View> controlComponents;
    private RelativeLayout rootLayout;
    private boolean isHandleMode = false;
    private boolean isActivityActive = false;
    private Handler mainHandler;
    private AlertDialog currentDialog;
    private boolean isBluetoothConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isActivityActive = true;
        mainHandler = new Handler(Looper.getMainLooper());

        try {
            // 获取当前模式
            isEditMode = getIntent().getBooleanExtra("EDIT_MODE", false);
            isHandleMode = getIntent().getBooleanExtra("HANDLE_MODE", false);

            // 设置横屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setContentView(R.layout.activity_control);

            // 保持屏幕常亮
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // 检查权限并初始化
            if (checkAndRequestPermissions()) {
                initializeAll();
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreate失败: " + e.getMessage());
            safeFinish();
        }
    }

    private void safeFinish() {
        if (isActivityActive && !isFinishing()) {
            finish();
        }
    }

    private boolean checkAndRequestPermissions() {
        // Android 10 (API 29) 及以上版本不需要请求存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsNeeded = new ArrayList<>();
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(permission);
                }
            }

            if (!permissionsNeeded.isEmpty()) {
                requestPermissions(permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    private void initializeAll() {
        try {
            initializeVariables();
            initializeViews();

            // 检查是否是首次运行
            isFirstTimeSetup = prefs.getBoolean(KEY_FIRST_TIME, true);

            setupControlComponents();

            if (isFirstTimeSetup) {
                // 首次运行，显示添加按钮
                setInitialButtonPositions();
            } else if (hasSavedLayout()) {
                // 非首次运行，加载上次保存的布局
                loadButtonPositions();
            } else {
                // 没有保存的布局，使用默认布局
                setInitialButtonPositions();
            }

            if (isEditMode) {
                addEditModeControls();
            }

            setupButtonListeners();
            setupBluetoothListeners();
            checkBluetoothConnection();
        } catch (Exception e) {
            Log.e(TAG, "初始化失败: " + e.getMessage());
            showError("初始化失败，请重试");
            safeFinish();
        }
    }
    private void initializeVariables() {
        try {
            bluetoothService = BluetoothService.getInstance(this);
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            controlComponents = new HashMap<>();
            rootLayout = findViewById(R.id.rootLayout);
            isBluetoothConnected = bluetoothService.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "初始化变量失败: " + e.getMessage());
            throw e;
        }
    }

    private void initializeViews() {
        try {
            energyBar = findViewById(R.id.energyBar);
            energyBar.setMax(100);

            // 设置能量条样式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                energyBar.setProgressDrawable(getResources().getDrawable(
                        R.drawable.custom_energy_bar, getTheme()));
            } else {
                energyBar.setProgressDrawable(getResources().getDrawable(
                        R.drawable.custom_energy_bar));
            }

            // 禁用能量条的拖动功能
            energyBar.setEnabled(false);

            // 确保能量条始终固定在顶部
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) energyBar.getLayoutParams();
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            energyBar.setLayoutParams(params);

            startEnergyBarAnimation(0);
        } catch (Exception e) {
            Log.e(TAG, "初始化视图失败: " + e.getMessage());
            throw e;
        }
    }

    // 能量条动画
    private void startEnergyBarAnimation(int newProgress) {
        if (!isActivityActive || isFinishing() || energyBar == null) {
            Log.w(TAG, "无法开始能量条动画：Activity状态无效或能量条为空");
            return;
        }

        try {
            Log.d(TAG, "开始能量条动画 - 当前值: " + energyBar.getProgress() + ", 目标值: " + newProgress);

            // 创建进度动画
            ObjectAnimator animation = ObjectAnimator.ofInt(
                    energyBar,
                    "progress",
                    energyBar.getProgress(),
                    newProgress
            );

            // 设置动画属性
            animation.setDuration(1000); // 1秒动画时长
            animation.setInterpolator(new DecelerateInterpolator());

            // 添加动画更新监听
            animation.addUpdateListener(valueAnimator -> {
                if (isActivityActive && !isFinishing()) {
                    int progress = (Integer) valueAnimator.getAnimatedValue();
                    Log.d(TAG, "能量条动画更新 - 当前进度: " + progress);
                    updateEnergyBarColor(progress);
                }
            });

            // 添加动画监听器
            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    Log.d(TAG, "能量条动画开始");
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    Log.d(TAG, "能量条动画结束，最终值: " + newProgress);
                }
            });

            // 在主线程中开始动画
            mainHandler.post(() -> {
                animation.start();
                Log.d(TAG, "能量条动画已启动");
            });

        } catch (Exception e) {
            Log.e(TAG, "能量条动画更新失败: " + e.getMessage());
            // 如果动画失败，直接设置进度
            if (energyBar != null) {
                energyBar.setProgress(newProgress);
                updateEnergyBarColor(newProgress);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        Log.d(TAG, "Activity恢复，重新连接蓝牙服务");
        setupBluetoothListeners();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        Log.d(TAG, "Activity暂停，清理资源");
        cleanupResources();
    }

    private void cleanupResources() {
        try {
            if (bluetoothService != null) {
                bluetoothService.removeConnectionStateChangeListener(this);
                Log.d(TAG, "已移除蓝牙连接状态监听器");
            }

            // 清理动画资源
            if (energyBar != null) {
                energyBar.clearAnimation();
                Log.d(TAG, "已清理能量条动画");
            }

        } catch (Exception e) {
            Log.e(TAG, "清理资源时发生错误: " + e.getMessage());
        }
    }

    // 更新能量条颜色
    private void updateEnergyBarColor(int progress) {
        if (energyBar == null) {
            Log.w(TAG, "无法更新能量条颜色：能量条为空");
            return;
        }

        try {
            LayerDrawable layerDrawable = (LayerDrawable) energyBar.getProgressDrawable();
            ClipDrawable progressDrawable = (ClipDrawable) layerDrawable.findDrawableByLayerId(android.R.id.progress);

            // 根据进度值计算颜色
            int color;
            if (progress >= 75) {
                // 绿色 (75-100)
                color = Color.rgb(76, 175, 80);
                Log.d(TAG, "能量条颜色更新为绿色，当前进度: " + progress);
            } else if (progress >= 50) {
                // 黄色 (50-74)
                color = Color.rgb(255, 235, 59);
                Log.d(TAG, "能量条颜色更新为黄色，当前进度: " + progress);
            } else if (progress >= 25) {
                // 橙色 (25-49)
                color = Color.rgb(255, 152, 0);
                Log.d(TAG, "能量条颜色更新为橙色，当前进度: " + progress);
            } else {
                // 红色 (0-24)
                color = Color.rgb(244, 67, 54);
                Log.d(TAG, "能量条颜色更新为红色，当前进度: " + progress);
            }

            // 设置渐变色
            GradientDrawable gradientDrawable = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{color, adjustBrightness(color, 1.2f)}
            );
            float density = getResources().getDisplayMetrics().density;
            gradientDrawable.setCornerRadius(8 * density); // 将 8dp 转换为像素

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressDrawable.setDrawable(gradientDrawable);
            } else {
                progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }

            Log.d(TAG, "能量条颜色更新完成");

        } catch (Exception e) {
            Log.e(TAG, "更新能量条颜色失败: " + e.getMessage());
        }
    }

    // 辅助方法：调整颜色亮度
    private int adjustBrightness(int color, float factor) {
        int red = Math.min(255, (int) (Color.red(color) * factor));
        int green = Math.min(255, (int) (Color.green(color) * factor));
        int blue = Math.min(255, (int) (Color.blue(color) * factor));
        return Color.rgb(red, green, blue);
    }

    private void setupBluetoothListeners() {
        if (!isActivityActive) return;

        try {
            Log.d(TAG, "开始设置蓝牙监听器...");

            // 添加蓝牙连接状态监听
            bluetoothService.addConnectionStateChangeListener(this);

            // 设置数据接收监听器
            bluetoothService.setDataReceiveListener(data -> {
                if (isActivityActive && !isFinishing()) {
                    try {
                        // 记录原始数据
                        Log.d(TAG, "收到蓝牙数据: " + data);

                        // 尝试解析数据
                        int energy = Integer.parseInt(data.trim());
                        Log.d(TAG, "解析后的能量值: " + energy);

                        // 验证数据范围
                        if (energy < 0 || energy > 100) {
                            Log.w(TAG, "能量值超出范围(0-100): " + energy);
                            energy = Math.min(100, Math.max(0, energy));
                        }

                        // 使用final变量在lambda表达式中
                        final int validEnergy = energy;

                        // 在主线程中更新UI
                        mainHandler.post(() -> {
                            Log.d(TAG, "开始更新能量条动画, 目标值: " + validEnergy);
                            startEnergyBarAnimation(validEnergy);
                        });

                    } catch (NumberFormatException e) {
                        Log.e(TAG, "无法解析能量值数据: " + data);
                    } catch (Exception e) {
                        Log.e(TAG, "处理能量值数据时发生错误: " + e.getMessage());
                    }
                } else {
                    Log.w(TAG, "Activity不活跃，忽略数据: " + data);
                }
            });

            Log.d(TAG, "蓝牙监听器设置完成");

        } catch (Exception e) {
            Log.e(TAG, "设置蓝牙监听器失败: " + e.getMessage());
            showError("蓝牙监听器设置失败");
        }
    }

    private void checkBluetoothConnection() {
        if (!isActivityActive) return;

        try {
            if (!bluetoothService.isConnected() && isHandleMode) {
                mainHandler.post(this::showDisconnectionDialog);
            }
        } catch (Exception e) {
            Log.e(TAG, "检查蓝牙连接失败: " + e.getMessage());
            showError("蓝牙连接检查失败");
            safeFinish();
        }
    }

    private void showDisconnectionDialog() {
        if (!isActivityActive || isFinishing()) return;

        try {
            dismissCurrentDialog();
            currentDialog = new AlertDialog.Builder(this)
                    .setTitle("蓝牙连接断开")
                    .setMessage("蓝牙连接已断开，将返回主页面")
                    .setCancelable(false)
                    .setPositiveButton("确定", (dialog, which) -> {
                        dialog.dismiss();
                        returnToMainActivity();
                    })
                    .create();

            if (isActivityActive && !isFinishing()) {
                currentDialog.show();
            }
        } catch (Exception e) {
            Log.e(TAG, "显示断开连接对话框失败: " + e.getMessage());
            returnToMainActivity();
        }
    }

    private void showToast(String message) {
        if (isActivityActive && !isFinishing()) {
            mainHandler.post(() ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void returnToMainActivity() {
        if (!isActivityActive || isFinishing()) return;

        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "返回主页面失败: " + e.getMessage());
            safeFinish();
        }
    }

    private void showError(String message) {
        if (!isActivityActive || isFinishing()) return;

        mainHandler.post(() -> {
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "显示错误消息失败: " + e.getMessage());
            }
        });
    }

    @Override
    public void onConnectionStateChanged(boolean connected) {
        if (!isActivityActive || isFinishing()) return;

        isBluetoothConnected = connected;
        mainHandler.post(() -> {
            if (!connected && isHandleMode) {
                showDisconnectionDialog();
            }
        });
    }

    private void setupAddButtonListener() {
        if (!isActivityActive || buttonAdd == null) return;

        try {
            buttonAdd.setOnClickListener(v -> {
                if (remainingComponents.isEmpty()) {
                    showToast("所有组件已添加完成");
                    buttonAdd.setVisibility(View.GONE);
                    saveCurrentLayout();
                    return;
                }

                // 显示下一个待添加的组件
                View nextComponent = remainingComponents.get(0);
                nextComponent.setVisibility(View.VISIBLE);
                nextComponent.setOnTouchListener(this::handleEditMode);

                // 将组件从待添加列表移到已添加列表
                remainingComponents.remove(0);
                addedComponents.add(nextComponent);

                // 如果所有组件都已添加，隐藏添加按钮
                if (remainingComponents.isEmpty()) {
                    buttonAdd.setVisibility(View.GONE);
                    showToast("所有组件已添加完成");
                    saveCurrentLayout();
                } else {
                    // 显示剩余组件数量
                    showToast("还剩" + remainingComponents.size() + "个组件待添加");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "设置添加按钮监听器失败: " + e.getMessage());
            showError("添加按钮初始化失败");
        }
    }

    @SuppressLint("FindViewByIdCast")
    private void setupControlComponents() {
        if (!isActivityActive) return;

        try {
            // 初始化添加按钮
            buttonAdd = findViewById(R.id.buttonAdd);
            remainingComponents = new ArrayList<>();
            addedComponents = new ArrayList<>();

            // 将所有控制组件添加到Map中
            controlComponents.put(R.id.buttonV, findViewById(R.id.buttonV));
            controlComponents.put(R.id.buttonW, findViewById(R.id.buttonW));
            controlComponents.put(R.id.buttonS, findViewById(R.id.buttonS));
            controlComponents.put(R.id.buttonA, findViewById(R.id.buttonA));
            controlComponents.put(R.id.buttonD, findViewById(R.id.buttonD));
            controlComponents.put(R.id.buttonB, findViewById(R.id.buttonB));
            controlComponents.put(R.id.buttonN, findViewById(R.id.buttonN));
            controlComponents.put(R.id.buttonM, findViewById(R.id.buttonM));
            controlComponents.put(R.id.buttonE, findViewById(R.id.buttonE));

            if (isEditMode) {
                if (isFirstTimeSetup) {
                    // 首次设置时，隐藏所有控制组件
                    for (View component : controlComponents.values()) {
                        if (component.getId() != R.id.energyBar) {
                            component.setVisibility(View.GONE);
                            remainingComponents.add(component);
                        }
                    }
                    // 显示添加按钮
                    buttonAdd.setVisibility(View.VISIBLE);
                    // 设置添加按钮点击监听器
                    setupAddButtonListener();
                } else {
                    // 非首次设置时，所有组件可见且可拖动
                    buttonAdd.setVisibility(View.GONE);
                    for (View component : controlComponents.values()) {
                        if (component.getId() != R.id.energyBar) {
                            component.setVisibility(View.VISIBLE);
                            component.setOnTouchListener(this::handleEditMode);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "设置控制组件失败: " + e.getMessage());
            throw e;
        }
    }

    private boolean hasSavedLayout() {
        try {
            for (int viewId : controlComponents.keySet()) {
                if (prefs.contains(viewId + "_x") && prefs.contains(viewId + "_y")) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查保存布局失败: " + e.getMessage());
        }
        return false;
    }

    private void loadButtonPositions() {
        if (!isActivityActive) return;

        try {
            for (Map.Entry<Integer, View> entry : controlComponents.entrySet()) {
                int viewId = entry.getKey();
                View view = entry.getValue();

                if (view != null && prefs.contains(viewId + "_x") && prefs.contains(viewId + "_y")) {
                    float x = prefs.getFloat(viewId + "_x", view.getX());
                    float y = prefs.getFloat(viewId + "_y", view.getY());
                    view.setX(x);
                    view.setY(y);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "加载按钮位置失败: " + e.getMessage());
            showError("加载布局失败");
        }
    }

    private void setInitialButtonPositions() {
        if (!isActivityActive || hasSavedLayout()) return;

        try {
            // 设置顶部三个圆形按钮位置
            View buttonB = controlComponents.get(R.id.buttonB);
            View buttonN = controlComponents.get(R.id.buttonN);
            View buttonM = controlComponents.get(R.id.buttonM);
            View buttonE = controlComponents.get(R.id.buttonE);

            int centerX = getResources().getDisplayMetrics().widthPixels / 2;
            if (buttonB != null) {
                buttonB.setX(centerX - 120);
                buttonB.setY(20);
            }
            if (buttonN != null) {
                buttonN.setX(centerX - 25);
                buttonN.setY(20);
            }
            if (buttonM != null) {
                buttonM.setX(centerX + 70);
                buttonM.setY(20);
            }
            if (buttonE != null) {  //
                buttonE.setX(centerX + 165);
                buttonE.setY(20);
            }
            // 设置V按钮位置
            View buttonV = controlComponents.get(R.id.buttonV);
            if (buttonV != null) {
                buttonV.setX(20);
                buttonV.setY(20);
            }

            // 设置方向按钮位置
            View buttonW = controlComponents.get(R.id.buttonW);
            View buttonS = controlComponents.get(R.id.buttonS);
            View buttonA = controlComponents.get(R.id.buttonA);
            View buttonD = controlComponents.get(R.id.buttonD);

            // 设置上下方向按钮
            if (buttonW != null) {
                buttonW.setX(20);
                buttonW.setY(20);
            }
            if (buttonS != null) {
                buttonS.setX(20);
                buttonS.setY(20);
            }

            // 设置左右方向按钮
            if (buttonA != null) {
                buttonA.setX(20);
                buttonA.setY(20);
            }
            if (buttonD != null) {
                buttonD.setX(20);
                buttonD.setY(20);
            }

            // 保存初始布局
            saveCurrentLayout();
        } catch (Exception e) {
            Log.e(TAG, "设置初始按钮位置失败: " + e.getMessage());
            showError("初始化布局失败");
        }
    }

    private void addEditModeControls() {
        if (!isActivityActive) return;

        try {
            // 显示提示消息
            showEditModeToast();

            // 创建保存按钮
            Button saveButton = createButton("保存布局", View.generateViewId());
            Button resetButton = createButton("重置布局", View.generateViewId());

            // 设置按钮布局参数
            RelativeLayout.LayoutParams saveBtnParams = createButtonLayoutParams(true);
            RelativeLayout.LayoutParams resetBtnParams = createButtonLayoutParams(false);
            resetBtnParams.addRule(RelativeLayout.LEFT_OF, saveButton.getId());

            // 添加按钮到布局
            rootLayout.addView(saveButton, saveBtnParams);
            rootLayout.addView(resetButton, resetBtnParams);

            // 设置按钮点击监听器
            saveButton.setOnClickListener(v -> saveCurrentLayout());
            resetButton.setOnClickListener(v -> showResetConfirmDialog());
        } catch (Exception e) {
            Log.e(TAG, "添加编辑模式控件失败: " + e.getMessage());
            showError("添加编辑控件失败");
        }
    }

    private void showEditModeToast() {
        String message = hasSavedLayout() ? "正在编辑上次保存的布局" : "正在编辑默认布局";
        showToast(message);
    }

    private Button createButton(String text, int id) {
        Button button = new Button(this);
        button.setText(text);
        button.setId(id);
        return button;
    }

    private RelativeLayout.LayoutParams createButtonLayoutParams(boolean isRightAligned) {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        if (isRightAligned) {
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }
        params.setMargins(0, 20, 20, 0);
        return params;
    }
    private boolean handleEditMode(View v, MotionEvent event) {
        if (!isActivityActive || v.getId() == R.id.energyBar) {
            return false;
        }

        try {
            float currentX = event.getRawX();
            float currentY = event.getRawY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = currentX;
                    lastTouchY = currentY;
                    isDragging = true;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        // 计算移动距离
                        float deltaX = currentX - lastTouchX;
                        float deltaY = currentY - lastTouchY;

                        // 更新视图位置
                        float newX = v.getX() + deltaX;
                        float newY = v.getY() + deltaY;

                        // 获取父视图的边界
                        int parentWidth = rootLayout.getWidth();
                        int parentHeight = rootLayout.getHeight();
                        int viewWidth = v.getWidth();
                        int viewHeight = v.getHeight();

                        // 确保视图不会移出屏幕边界
                        newX = Math.max(0, Math.min(newX, parentWidth - viewWidth));
                        newY = Math.max(0, Math.min(newY, parentHeight - viewHeight));

                        // 设置新位置
                        v.setX(newX);
                        v.setY(newY);

                        // 更新最后的触摸位置
                        lastTouchX = currentX;
                        lastTouchY = currentY;
                        isLayoutChanged = true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "处理编辑模式触摸事件失败: " + e.getMessage());
            isDragging = false;
            return false;
        }
        return true;
    }

    private void setupButtonListeners() {
        if (!isActivityActive || isEditMode) return;

        try {
            // 设置V按钮点击监听
            Button buttonV = findViewById(R.id.buttonV);
            if (buttonV != null) {
                buttonV.setOnClickListener(v -> {
                    safeSendBluetoothMessage("V");
                    vibrateDevice(100);
                });
            }

            // 设置圆形按钮
            setupCircleButton(R.id.buttonB, "B");
            setupCircleButton(R.id.buttonN, "N");
            setupCircleButton(R.id.buttonM, "M");
            setupCircleButton(R.id.buttonE, "E");

            // 设置方向按钮
            setupDirectionalButton(R.id.buttonW, "W", "H");
            setupDirectionalButton(R.id.buttonS, "S", "J");
            setupDirectionalButton(R.id.buttonA, "A", "K");
            setupDirectionalButton(R.id.buttonD, "D", "L");
        } catch (Exception e) {
            Log.e(TAG, "设置按钮监听器失败: " + e.getMessage());
            showError("按钮初始化失败");
        }
    }

    private void setupCircleButton(int buttonId, final String message) {
        Button button = findViewById(buttonId);
        if (button != null) {
            button.setOnClickListener(v -> safeSendBluetoothMessage(message));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDirectionalButton(int buttonId, final String pressMessage, final String releaseMessage) {
        Button button = findViewById(buttonId);
        if (button != null) {
            button.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 按下动画效果
                        animateButtonPress(v);
                        safeSendBluetoothMessage(pressMessage);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 释放动画效果
                        animateButtonRelease(v);
                        safeSendBluetoothMessage(releaseMessage);
                        break;
                }
                return true;
            });
        }
    }

    // 添加按下动画效果的方法
    private void animateButtonPress(View button) {
        // 创建缩放动画
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f);
        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);

        // 创建颜色变化动画（可选）
        ObjectAnimator colorAnim = ObjectAnimator.ofInt(button, "backgroundColor",
                Color.parseColor("#FFFFFF"),  // 开始颜色（白色）
                Color.parseColor("#E0E0E0")); // 结束颜色（浅灰色）
        colorAnim.setEvaluator(new ArgbEvaluator());
        colorAnim.setDuration(100);

        // 组合动画
        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.play(scaleDownX).with(scaleDownY).with(colorAnim);
        scaleDown.start();
    }

    // 添加释放动画效果的方法
    private void animateButtonRelease(View button) {
        // 创建恢复动画
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1f);
        scaleUpX.setDuration(200);
        scaleUpY.setDuration(200);

        // 创建颜色恢复动画（可选）
        ObjectAnimator colorAnim = ObjectAnimator.ofInt(button, "backgroundColor",
                Color.parseColor("#E0E0E0"), // 开始颜色（浅灰色）
                Color.parseColor("#FFFFFF")); // 结束颜色（白色）
        colorAnim.setEvaluator(new ArgbEvaluator());
        colorAnim.setDuration(200);

        // 组合动画
        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.play(scaleUpX).with(scaleUpY).with(colorAnim);
        scaleUp.start();
    }

    private void safeSendBluetoothMessage(String message) {
        if (isActivityActive && bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendMessage(message);
        }
    }

    private void vibrateDevice(long duration) {
        if (vibrator != null) {
            try {
                vibrator.vibrate(duration);
            } catch (Exception e) {
                Log.e(TAG, "振动失败: " + e.getMessage());
            }
        }
    }

    private void showRemainingComponentsDialog(String message) {
        if (!isActivityActive || isFinishing()) return;

        try {
            dismissCurrentDialog(); // 确保之前的对话框被关闭

            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("未放置的组件")
                    .setMessage(message)
                    .setCancelable(false);  // 防止点击对话框外部关闭

            // 如果是退出时的提示
            if (message.contains("确定要退出")) {
                builder.setPositiveButton("退出", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                });
                builder.setNegativeButton("继续编辑", (dialog, which) -> {
                    dialog.dismiss();
                    if (buttonAdd != null) {
                        buttonAdd.performClick(); // 自动触发添加按钮
                    }
                });
            }
            // 如果是保存时的提示
            else if (message.contains("请点击添加按钮")) {
                builder.setPositiveButton("继续编辑", (dialog, which) -> {
                    dialog.dismiss();
                    if (buttonAdd != null) {
                        buttonAdd.performClick(); // 自动触发添加按钮
                    }
                });
                builder.setNegativeButton("强制保存", (dialog, which) -> {
                    dialog.dismiss();
                    // 移除所有未放置的组件
                    remainingComponents.clear();
                    // 执行保存操作
                    saveCurrentLayout();
                });
            }
            // 默认情况（一般提示）
            else {
                builder.setPositiveButton("确定", (dialog, which) -> {
                    dialog.dismiss();
                    if (buttonAdd != null) {
                        buttonAdd.performClick();
                    }
                });
            }

            currentDialog = builder.create();

            // 设置对话框显示位置（居中偏上）
            Window window = currentDialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                params.y = 200; // 距离顶部200像素
                window.setAttributes(params);
            }

            currentDialog.show();

            // 设置对话框文字大小和颜色
            TextView messageView = currentDialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextSize(16);
                messageView.setTextColor(Color.BLACK);
            }

            // 设置按钮文字颜色
            currentDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
            currentDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.colorAccent));

        } catch (Exception e) {
            Log.e(TAG, "显示未放置组件对话框失败: " + e.getMessage());
            showToast("显示对话框失败");
        }
    }

    private void dismissCurrentDialog() {
        try {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
                currentDialog = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭对话框失败: " + e.getMessage());
        }
    }

    private void saveCurrentLayout() {
        if (!isActivityActive) return;

        try {
            if (isFirstTimeSetup && !remainingComponents.isEmpty()) {
                showRemainingComponentsDialog("还有" + remainingComponents.size() +
                        "个组件未添加，请点击添加按钮继续添加组件");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || checkAndRequestPermissions()) {
                SharedPreferences.Editor editor = prefs.edit();

                // 保存组件位置
                for (Map.Entry<Integer, View> entry : controlComponents.entrySet()) {
                    int viewId = entry.getKey();
                    if (viewId != R.id.energyBar) {
                        View view = entry.getValue();
                        if (view != null) {
                            editor.putFloat(viewId + "_x", view.getX());
                            editor.putFloat(viewId + "_y", view.getY());
                        }
                    }
                }

                // 标记非首次运行
                if (isFirstTimeSetup) {
                    editor.putBoolean(KEY_FIRST_TIME, false);
                    isFirstTimeSetup = false;
                }

                editor.apply();
                showToast("布局已保存");
                isLayoutChanged = false;
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "保存布局失败: " + e.getMessage());
            showError("保存布局失败");
        }
    }

    private void showResetConfirmDialog() {
        if (!isActivityActive || isFinishing()) return;

        try {
            new AlertDialog.Builder(this)
                    .setTitle("重置布局")
                    .setMessage("确定要重置所有控件位置吗？")
                    .setPositiveButton("确定", (dialog, which) -> resetLayout())
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "显示重置确认对话框失败: " + e.getMessage());
        }
    }

    private void resetLayout() {
        if (!isActivityActive) return;

        try {
            // 清除所有保存的数据
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            // 重置为首次运行状态
            editor.putBoolean(KEY_FIRST_TIME, true);
            editor.apply();

            isFirstTimeSetup = true;

            // 重新初始化所有组件
            setupControlComponents();
            setInitialButtonPositions();

            showToast("布局已重置为初始状态");
            isLayoutChanged = false;
        } catch (Exception e) {
            Log.e(TAG, "重置布局失败: " + e.getMessage());
            showError("重置布局失败");
        }
    }

    // 修改现有的 onKeyDown 方法
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                safeSendBluetoothMessage("O");
                vibrateDevice(100);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:    // 添加音量减少键处理
                safeSendBluetoothMessage("Q");
                vibrateDevice(100);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // 修改现有的 onKeyUp 方法
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                safeSendBluetoothMessage("P");
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:    // 添加音量减少键处理
                safeSendBluetoothMessage("X");
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }



    @Override
    protected void onDestroy() {
        isActivityActive = false;
        dismissCurrentDialog();

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception e) {
                Log.e(TAG, "取消振动失败: " + e.getMessage());
            }
        }

        if (bluetoothService != null) {
            bluetoothService.setDataReceiveListener(null);
            bluetoothService.removeConnectionStateChangeListener(this);
        }

        super.onDestroy();
    }
}