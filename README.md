# USB 智能控制

一个运行在 `system_server` 中的 LSPosed 模块，用来根据 USB 连接和游戏状态自动切换 USB 调试与文件传输。

## 功能

- 收到电脑端 USB 数据握手后开启调试，断开后关闭
- 连接电脑时自动切换到 MTP，普通充电不会触发
- 可设置只在解锁后启用 MTP
- 横屏时临时关闭 USB 调试
- 指定游戏进入前台时临时关闭 USB 调试

模块由 USB、解锁、屏幕方向、前台应用和配置变化事件触发，不常驻轮询。断开使用短防抖，模块切换 USB 功能时单独保留重枚举缓冲，避免调试和文件传输反复开关。

连接电脑时可以同时充电，模块不会单凭“正在充电”判断是否有数据连接。

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

1. 从 GitHub Releases 下载并安装 `USB-Smart-Control.apk`。
2. 在 LSPosed 中启用模块。
3. 重启手机。
4. 打开“USB 智能控制”，选择需要的功能并保存。

游戏列表采用 Android 系统的游戏分类。没有被系统标记为游戏的应用不会出现在列表中。

## 注意

模块会修改 USB 调试和 USB 主功能。系统无法确认 USB 数据状态时，不会把普通连接直接当作数据连接。测试前建议保留无线调试或其他可用的恢复方式。不同厂商的系统实现可能存在差异。
