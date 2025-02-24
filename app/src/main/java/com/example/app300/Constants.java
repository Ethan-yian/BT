/**
 * 应用程序常量定义类
 * 集中管理应用中使用的各种常量值
 * 创建时间：2025-01-13
 * 创建者：Ethan-yian
 */
package com.example.app300;

public class Constants {
    /**
     * 请求启用蓝牙的请求码
     * 用于startActivityForResult的请求标识
     */
    public static final int REQUEST_ENABLE_BT = 1;

    /**
     * 请求权限的请求码
     * 用于requestPermissions的请求标识
     */
    public static final int REQUEST_PERMISSIONS = 2;

    /**
     * 蓝牙扫描持续时间（毫秒）
     * 定义单次蓝牙扫描的最大时长
     * 当前设置为10秒 = 10000毫秒
     */
    public static final long SCAN_PERIOD = 10000; // 10 seconds

    /**
     * SharedPreferences文件名
     * 用于保存控制布局的位置信息
     * 在ControlActivity中使用此常量访问存储的布局数据
     */
    public static final String PREFS_NAME = "ControlLayout";

    /**
     * 震动反馈的持续时间（毫秒）
     * 定义按钮点击等操作的触觉反馈时长
     * 当前设置为100毫秒
     */
    public static final long VIBRATION_DURATION = 100; // 100ms
}