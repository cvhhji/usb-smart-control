# USB 智能控制

一个运行在 `system_server` 中的 LSPosed 模块，用来根据 USB 连接和游戏状态自动切换 USB 调试与文件传输。

## 功能

- USB 接入后开启调试，断开后关闭
- USB 接入后切换到 MTP 文件传输
- 可设置只在解锁后启用 MTP
- 横屏时临时关闭 USB 调试
- 指定游戏进入前台时临时关闭 USB 调试
- 自动列出系统标记为游戏的应用
- 在应用内查看模块激活状态
- 支持浅色和暗色界面

模块由 USB、解锁、屏幕方向、前台应用和配置变化事件触发，没有定时轮询。

## 要求

- Android 8.0 或更高版本
- 支持现代 libxposed API 102 的 LSPosed 环境
- 模块作用域 `system_server`

模块声明：

```properties
minApiVersion=102
targetApiVersion=102
staticScope=true
```

## 使用

1. 安装 APK。
2. 在 LSPosed 中启用模块。
3. 重启手机。
4. 打开“USB 智能控制”，选择需要的功能并保存。

游戏列表采用 Android 系统的游戏分类。没有被系统标记为游戏的应用不会出现在列表中。

## 注意

模块会修改 USB 调试和 USB 主功能。测试前建议保留无线调试或其他可用的恢复方式。不同厂商的系统实现可能存在差异。
