# systrace

systrace 是分析 Android 设备性能的主要工具。不过，它实际上是其他工具的封装容器：它是 atrace 的主机端封装容器，是用于控制用户空间跟踪和设置ftrace的设备端可执行文件，也是Linux内核中的主要跟踪机制。systrace 使用 atrace 来启用跟踪，然后读取 ftrace 缓冲区并将其全部封装到一个独立的 HTML 查看器中（虽然较新的内核支持 Linux 增强型柏克莱封包过滤器 (eBPF)，但以下文档内容仅适用于 3.18 内核（无 eFPF），因为 Pixel/Pixel XL 上使用的是 3.18 内核）。

systrace 归 Google Android 和 Google Chrome 团队所有，且是作为 Catapult 项目的一部分在开源环境中开发的。除 systrace 之外，Catapult 还包括其他有用的实用程序。例如，除了可由 systrace 或 atrace 直接启用的功能之外，ftrace 还提供了其他功能，并且包含一些对调试性能问题至关重要的高级功能（这些功能需要 root 访问权限，通常也需要新的内核）。

在使用 systrace 的过程中，请记住，每个事件都是由 CPU 上的活动触发的。

**注意**：硬件中断不受 CPU 控制，且会在 ftrace 中触发事件，不过，向跟踪日志的实际提交操作是由中断处理程序完成的，如果您的中断已到达，而（例如）某个其他不良驱动程序已停用中断，则提交可能会延迟。因此，CPU是关键要素。

因为 systrace 构建于 ftrace 之上，而 ftrace 在 CPU 上运行，所以 CPU 上的活动必须写入用于记录硬件变化情况的 ftrace 缓冲区。这意味着，如果您想知道显示栅栏更改状态的原因，则可以查看在状态转换的确切点 CPU 上运行了哪些活动（在 CPU 上运行的某些活动在日志中触发了这种更改）。此概念是使用 systrace 分析性能的基础。

## show current window

```
adb shell dumpsys window | grep mCurrentFocus
```

## command

```
./systrace.py -t 20 gfx input view webview wm am res sched freq idle disk  -a com.google.android.wearable.app
```

## issues

```
Unable to select a master clock domain because no path can be found from "SYSTRACE" to "LINUX_FTRACE_GLOBAL".解决方法

在chrome浏览器的地址栏中输入：chrome://tracing
之后点击左上角的load加载你生成的test.log.html文件就可以正常查看。
```

## trace app

```
./systrace.py -t 30 app -a com.android.startop.colorchanging
```

like this:

[App Trace](./app-systrace.png)

## Refs

https://www.jianshu.com/p/6bce4e256381