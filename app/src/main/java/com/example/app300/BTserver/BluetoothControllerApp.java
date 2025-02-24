/**
 * 蓝牙控制器应用的全局 Application 类
 * 用于提供全局的应用程序上下文和单例访问点
 * 必须在 AndroidManifest.xml 中声明该类作为 application 标签的 android:name
 */
package com.example.app300.BTserver;

import android.app.Application;

public class BluetoothControllerApp extends Application {
    // 静态实例，用于实现单例模式
    private static BluetoothControllerApp instance;

    /**
     * 应用程序创建时的回调方法
     * 在应用程序启动时由系统调用，早于任何 Activity 的创建
     * 用于初始化全局的应用程序状态
     */
    @Override
    public void onCreate() {
        super.onCreate();
        // 保存实例引用，以便后续通过 getInstance() 方法访问
        instance = this;
    }

    /**
     * 获取 Application 实例的静态方法
     * 提供了一个全局访问点，使得应用程序的其他部分可以方便地获取 Application 上下文
     *
     * @return BluetoothControllerApp 的单例实例
     */
    public static BluetoothControllerApp getInstance() {
        return instance;
    }
}