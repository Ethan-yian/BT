## 蓝牙按键信息发送说明

### 1. 按钮信息
#### 1.1 方向控制按钮
- **W按钮**
  - 按下发送: "W"
  - 释放发送: "H"
- **S按钮**
  - 按下发送: "S"
  - 释放发送: "J"
- **A按钮**
  - 按下发送: "A"
  - 释放发送: "K"
- **D按钮**
  - 按下发送: "D"
  - 释放发送: "L"

#### 1.2 功能按钮
- **V按钮**
  - 点击发送: "V"
  - 特点：触发时会伴随100ms振动反馈
- **圆形按钮组**
  - 灯光控制按钮（B按钮）
    - 点击发送: "B"
    - 特点：灯泡图标显示
  - 减速按钮（N按钮）
    - 点击发送: "N"
    - 显示："-"符号
  - 加速按钮（M按钮）
    - 点击发送: "M"
    - 显示："+"符号
  - 风扇控制按钮（E按钮）
    - 点击发送: "E"
    - 特点：风扇图标显示

### 2. 音量键控制
#### 2.1 音量增加键
- 按下发送: "O"
- 释放发送: "P"
- 特点：按下时会伴随100ms振动反馈

#### 2.2 音量减少键
- 按下发送: "Q"
- 释放发送: "X"
- 特点：按下时会伴随100ms振动反馈

### 3. 信号发送特点
1. **实时性**：所有按键信号都是实时发送
2. **错误处理**：只在蓝牙连接状态下发送信号
3. **按键反馈**：部分按键（如V键和音量键）带有振动反馈
4. **动画效果**：方向按键带有按下和释放的动画效果
5. **图标显示**：使用直观的图标显示按钮功能（灯泡、风扇图标）

### 4. 相关代码示例
```java
// 方向按钮信号发送示例
private void setupDirectionalButton(int buttonId, final String pressMessage, final String releaseMessage) {
    Button button = findViewById(buttonId);
    if (button != null) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    safeSendBluetoothMessage(pressMessage);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    safeSendBluetoothMessage(releaseMessage);
                    break;
            }
            return true;
        });
    }
}

// 音量键信号处理示例
@Override
public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
        case KeyEvent.KEYCODE_VOLUME_UP:
            safeSendBluetoothMessage("O");
            vibrateDevice(100);
            return true;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            safeSendBluetoothMessage("Q");
            vibrateDevice(100);
            return true;
        default:
            return super.onKeyDown(keyCode, event);
    }
}
```

### 5. 信号处理流程
1. 按键触发事件
2. 检查蓝牙连接状态
3. 发送对应信号
4. 执行相应的反馈（振动/动画）
5. 等待按键释放
6. 发送释放信号（如果有）