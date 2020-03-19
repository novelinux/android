# perfetto

Perfetto工具是Android下一代全新的统一的trace收集和分析框架，可以抓取平台和app的trace信息，是用来取代systrace的，但systrace由于历史原因也还会一直存在，并且Perfetto抓取的trace文件也可以同样转换成systrace视图，如果习惯用systrace的，可以用Perfetto UI的"Open with legacy UI"转换成systrace视图来看.

使用 perfetto 工具，您可以通过 Android 调试桥 (ADB) 在 Android 设备上收集性能信息。perfetto 从您的设备上收集性能跟踪数据时会使用多种来源，例如：

* 使用 ftrace 收集内核信息
* 使用 atrace 收集服务和应用中的用户空间注释
* 使用 heapprofd 收集服务和应用的本地内存使用情况信息

[perfetto 整体架构](./res/perfetto-stack.png)

## 特性

Perfetto的几个主要特点如下所示：

### 专为生产环境而设计

Perfetto的跟踪库和守护程序设计用于生产中。特权隔离是其关键的设计目标

### 长时间跟踪

可以在线抓取长时间的trace，可以长达一个小时，另外抓取的文件size也可以达到GB级别，这样就可以在后台开启，让它一直抓取trace了，特别适用于那种复现概率很低，又比较严重的性能问题。

### 互操作性

抓取到trace文件之后，它的格式是protobuf或者json，需要用到Perfetto的一个C++库，这个库可以基于protobuf或者json提供出来一个SQL语言操作的接口，这个C++库可以基于命令行，也可以集成到其他工具中，以允许与多种语言互操作。

### 可组合性

由于Perfetto设计用于OS级跟踪和应用程序级跟踪，因此其设计允许组合Perfetto跟踪库的多个实例，从而可以通过相同的前端进行驱动嵌套多层跟踪。这样可以将特定于应用程序和操作系统范围的跟踪事件进行强大的混合。

### 可扩展性

Perfetto具有很好的可扩展性，它除了提供标准的tracepoints之外，例如CPU调度信息，内存信息等，还可以通过atrace HAL层扩展，在Android P当中，Google新增加了一个atrace HAL层，atrace进程可以调用这个HAL的接口来获取当前的扩展信息，相关代码可见Google 提交，这样如果需要扩展tracepoints的话，就可以按照graphic的示例添加即可。

### 可配置性

* 提供全新的Perfetto UI网站，可以在上面通过选取开关的方式，自动生成抓取trace的命令，同时可以打开trace文件，自动把protobuf或者json转变成UI，另外还集成了几种预定义的trace分析统计工具，详情可见它的 Metrics and auditors 选项。

##  Running Perfetto

### 开启traced/traced_probes服务

```
adb shell setprop persist.traced.enable 1
```

执行上面的命令后，如果看到如下类似的Log，那么就说明开启成功了，也可以直接ps看有没有这两个进程。

```
adb logcat -s perfetto
perfetto: service.cc:45 Started traced, listening on /dev/socket/traced_producer /dev/socket/traced_consumer
perfetto: probes.cc:25 Starting /system/bin/traced_probes service
perfetto: probes_producer.cc:32 Connected to the service
```

或者

```
# ps -A | grep traced
nobody        1028     1   46368   4044 poll_schedule_timeout 0 S traced
nobody        1029     1   48416   4076 poll_schedule_timeout 0 S traced_probes
```

### 抓取trace

例如，以下命令会跟踪 sched/sched_switch 事件：

```
adb shell perfetto --out /data/local/tmp/trace sched/sched_switch
adb pull /data/local/tmp/trace .
```

### Analysis trace

* https://ui.perfetto.dev/

Open trace file, so we can see like this:

[Trace shced switch view](./res/trace_sched_switch.png)

## official website

* https://docs.perfetto.dev/#/