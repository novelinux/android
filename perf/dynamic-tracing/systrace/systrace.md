# systrace

systrace 是分析 Android 设备性能的主要工具。不过，它实际上是其他工具的封装容器：它是 atrace 的主机端封装容器，是用于控制用户空间跟踪和设置ftrace的设备端可执行文件，也是Linux内核中的主要跟踪机制。systrace 使用 atrace 来启用跟踪，然后读取 ftrace 缓冲区并将其全部封装到一个独立的 HTML 查看器中（虽然较新的内核支持 Linux 增强型柏克莱封包过滤器 (eBPF)，但以下文档内容仅适用于 3.18 内核（无 eFPF），因为 Pixel/Pixel XL 上使用的是 3.18 内核）。

systrace 归 Google Android 和 Google Chrome 团队所有，且是作为 Catapult 项目的一部分在开源环境中开发的。除 systrace 之外，Catapult 还包括其他有用的实用程序。例如，除了可由 systrace 或 atrace 直接启用的功能之外，ftrace 还提供了其他功能，并且包含一些对调试性能问题至关重要的高级功能（这些功能需要 root 访问权限，通常也需要新的内核）。

在使用 systrace 的过程中，请记住，每个事件都是由 CPU 上的活动触发的。

**注意**：硬件中断不受 CPU 控制，且会在 ftrace 中触发事件，不过，向跟踪日志的实际提交操作是由中断处理程序完成的，如果您的中断已到达，而（例如）某个其他不良驱动程序已停用中断，则提交可能会延迟。因此，CPU是关键要素。

因为 systrace 构建于 ftrace 之上，而 ftrace 在 CPU 上运行，所以 CPU 上的活动必须写入用于记录硬件变化情况的 ftrace 缓冲区。这意味着，如果您想知道显示栅栏更改状态的原因，则可以查看在状态转换的确切点 CPU 上运行了哪些活动（在 CPU 上运行的某些活动在日志中触发了这种更改）。此概念是使用 systrace 分析性能的基础。

Systrace 的功能包括跟踪系统的 I/O 操作、内核工作队列、CPU 负载以及 Android 各个子系统的运行状况等。在 Android 平台中，它主要由3部分组成：

* 内核部分：Systrace 利用了 Linux Kernel 中的 ftrace 功能。所以，如果要使用 Systrace 的话，必须开启 kernel 中和 ftrace 相关的模块。
* 数据采集部分：Android 定义了一个 Trace 类。应用程序可利用该类把统计信息输出给ftrace。同时，Android 还有一个 atrace 程序，它可以从 ftrace 中读取统计信息然后交给数据分析工具来处理。
* 数据分析工具：Android 提供一个 systrace.py（ python 脚本文件，位于 Android SDK目录/platform-tools/systrace 中，其内部将调用 atrace 程序）用来配置数据采集的方式（如采集数据的标签、输出文件名等）和收集 ftrace 统计数据并生成一个结果网页文件供用户查看。 从本质上说，Systrace 是对 Linux Kernel中 ftrace 的封装。应用进程需要利用 Android 提供的 Trace 类来使用 Systrace.

## example

```
./systrace.py -t 20 gfx input view webview wm am res sched freq idle disk  -a com.google.android.wearable.app
```

### issues

```
Unable to select a master clock domain because no path can be found from "SYSTRACE" to "LINUX_FTRACE_GLOBAL".解决方法

在chrome浏览器的地址栏中输入：chrome://tracing
之后点击左上角的load加载你生成的test.log.html文件就可以正常查看。
```

## systrace支持的事件：

* gfx - Graphics
* input - Input
* view - View
* webview - WebView
* wm - Window Manager
* am - Activity Manager
* audio - Audio
* video - Video
* camera - Camera
* hal - Hardware Modules
* res - Resource Loading
* dalvik - Dalvik VM
* rs - RenderScript
* sched - CPU Scheduling
* freq - CPU Frequency
* membus - Memory Bus Utilization
* idle - CPU Idle
* disk - Disk input and output
* load - CPU Load
* sync - Synchronization Manager
* workq - Kernel Workqueues Note: Some trace categories are not supported on all devices. Tip: If you want to see the names of tasks in the trace output, you must include the sched category in your command parameters.

## 线程状态

* 灰色：正在休眠。

线程没有工作要做，可能是因为线程在互斥锁上被阻塞。

* 蓝色：可运行（它可以运行，但是调度程序尚未选择让它运行）。

作用：Runnable 状态的线程状态持续时间越长，则表示 cpu 的调度越忙，没有及时处理到这个任务：

是否后台有太多的任务在跑？
没有及时处理是因为频率太低？
没有及时处理是因为被限制到某个 cpuset 里面，但是 cpu 很满？

* 绿色：正在运行（调度程序认为它正在运行）。
作用：查看其运行的时间，与竞品做对比，分析快或者慢的原因：

是否频率不够？
是否跑在了小核上？
是否频繁在 Running 和 Runnable 之间切换？为什么？
是否频繁在 Running 和 Sleep 之间切换？为什么？
是否跑在了不该跑的核上面？比如不重要的线程占用了超大核

* 红色：不可中断休眠（通常在内核中处于休眠锁定状态）。可以指示 I/O 负载，在调试性能问题时非常有用。

线程在另一个内核操作（通常是内存管理）上被阻塞, 例如page_fault

* 橙色：由于 I/O 负载而不可中断休眠。

**Linux 常见的进程状态*:

D 无法中断的休眠状态（通常 IO 的进程）
R 正在可运行队列中等待被调度的；
S 处于休眠状态；
T 停止或被追踪；
X 死掉的进程 （基本很少見）
Z 僵尸进程；

### 线程状态信息

[线程状态](./res/systrace-thread-info.png)

### 函数运行片段信息

[函数运行片段信息](./res/systrace-function-slice.png)

## show current window

```
adb shell dumpsys window | grep mCurrentFocus
```

## trace app example

```
./systrace.py -t 30 app -a com.android.startop.colorchanging
```

like this:

[App Trace](./app-systrace.png)

## Refs

https://www.jianshu.com/p/6bce4e256381