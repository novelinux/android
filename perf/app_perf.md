## App Performance

### Old

1.将启动页主题背景设置成闪屏页图片

这么做的目的主要是为了消除启动时的黑白屏，给用户一种秒响应的感觉，但是并不会真正减少用户启动时间，仅属于视觉优化。

2.主页面布局优化

1）通过减少冗余或者嵌套布局来降低视图层次结构
2）用 ViewStub 替代在启动过程中不需要显示的 UI 控件

3.Application 和 主 Activity 的 onCreate 中异步初始化某些代码

因为在主线程上进行资源初始化会降低启动速度，所以可以将不必要的资源初始化延迟，达到优化的效果。但是这里要注意懒加载集中化的问题，别用户启动时间快了，但是无法在界面上操作就尴尬了。

老三样并不说是不管用或者过时了，只是这三种优化方式都是非常基础的方式，当你的启动优化遇到了瓶颈，是不能够再通过这三种方式突破的。

### 1.通过 systrace 查找耗时代码

具体步骤:

1）清空手机后台
2）在命令行执行
```
python $ANDROID_HOME/platform-tools/systrace/systrace.py gfx view wm am pm ss dalvik app sched -b 90960 -a 你的包名 -o test.log.html
```

这一步需要你系统环境配置了 ANDROID_HOME 环境变量。

3）运行你的App，正常操作到你想测性能的地方，然后再命令行窗口中按 Enter 键停止收集
4）用 chrome（只支持此浏览器）打开生成的 test.log.html 结果文件，打开效果如下图：
[Test Systrace](./test-systrace.webp)

目前需要关心的地方就是我们的应用进程相关的，也就是红框圈起来的地方。
图中的 F 代表绘制帧，黄色/红色表示该帧绘制超时，绿色代表绘制正常,也就是在16.6ms内绘制完一帧。
放大可以看到在启动过程中，控件渲染耗时情况
所以可以很容易发现哪些启动过程中没有用到的 UI 控件也被渲染了，这时就可以用 ViewStub 去替代。

但是现在可以看到的都是系统调用的耗时情况，因为谷歌预先在代码里关键的地方加上了监控，如果想要看到自己方法的耗时.

那需要手动在方法入口加上 Trace.beginSection("TAG")
在方法结束的地方加上 Trace.endSection()

这样就可以在生成的结果中看到我们自定义的 tag。如果很多地方你都想加上监控，手动加是肯定不合适的，这里推荐函数插桩方式自动加上监控代码，参考 systrace+函数插桩这种方式不仅可以帮助监控启动过程中性能问题，再做卡顿优化的时候也可以用这种方式。定位到了耗时方法，再做一些针对性的优化就相对容易了。

### 2.通过 redex 重排列 class 文件

redex 是 Facebook 开源的一款字节码优化工具，目前只支持 mac 和 linux。
我们用的是里面的 interdex 功能来重排列我们 dex 中的 class 文件，那么为什么重排列 class 文件可以优化启动速度？
简单的说，通过文件重排列的目的，就是将启动阶段需要用到的文件在 APK 文件中排布在一起，尽可能的利用 Linux 文件系统的 pagecache 机制，用最少的磁盘 IO 次数，读取尽可能多的启动阶段需要的文件，减少 IO 开销，从而达到提升启动性能的目的。

具体可以参考支付宝的这篇 《通过安装包重排布优化 Android 端启动性能》
所以我们着手要做的就三件事：
* 1）安装配置 redex
* 2）获得启动过程中 class 文件的加载顺序
* 3）根据这个顺序重排列 dex 中的 class 文件


1）下载 redex，配置环境（Mac OS）

```
git clone https://github.com/facebook/redex.git
xcode-select --install
brew install autoconf automake libtool python3
brew install boost jsoncpp
```

2）编译安装 redex

```
cd redex
autoreconf -ivf && ./configure && make
sudo make install
```

3) 配置优化项

因为 redex 默认不开启 interdex，所以我们要在配置文件中加上相应的配置，在 redex 文档中有说明

所以我们打开配置文件

```
cd redex/config/
vi default.config
```

4）获得启动 class 加载顺序列表

这里按照 redex 提供的工具获取，但是需要手机有 root 权限
首先清空后台进程，然后打开你的应用
获取你的应用的 pid

```
adb shell ps | grep 你的应用包名
```

收集堆内存，需要 root 权限

```
adb root
adb shell am dumpheap YOUR_PID /data/local/tmp/SOMEDUMP.hprof
```

把堆内存文件拉取到电脑的某个位置

```
adb pull /data/local/tmp/SOMEDUMP.hprof YOUR_DIR_HERE/
```

通过 python 脚本解析堆内存，生成类加载顺序列表

```
python redex/tools/hprof/dump_classes_from_hprof.py --hprof YOUR_DIR_HERE/SOMEDUMP.hprof > list_of_classes.txt
```

ps: 这个脚本支持 Python 2，执行过程中如果遇到某个库没安装之类的，直接通过 pip install 缺失的库 就可以。

6）重新签名

这时候生成的 output.apk 是不能直接安装的，需要重新签名，我测试用的是 debug 包，所以重新签了debug 的签名

```
jarsigner -keystore ~/.android/debug.keystore -storepass android -keypass android output.apk androiddebugkey
```

关于 redex 的使用和相关配置文档，都可以在 redex/docs/ 目录下查看。

### 其他相关


启动耗时测量

为了正确诊断冷启动的性能，需要冷启动的时间指标，下面有两种简单的方式：

```
adb命令 : adb shell am start -S -W 包名/启动类的全名
```

例如：

```
adb shell am start -S -W com.android.helloword/com.android.helloword.MainActivity
```

* ThisTime : 最后一个 Activity 的启动耗时
* TotalTime : 启动一连串的 Activity 总耗时
* WaitTime : 应用进程的创建过程 + TotalTime

这里我们关注 TotalTime 就可以。

谷歌在 Android4.4（API 19）上也提供了测量方法，在 logcat 中过滤 Displayed 字段，输出的值表示在启动过程和完成在屏幕上绘制相应 Activity 之间经过的时间，其实和上面的方式得到的结果是一样的。

关于 Android App 的冷启动过程和一些概念可以参考谷歌官方文档 「App startup time 」
https://developer.android.com/topic/performance/vitals/launch-time


由于一些原因，还有一些优化方法没有实践，有兴趣的可以自行了解：

1）启动过程中的 GC 优化，尽量减少 GC 次数，避免大量或者频繁创建对象，如必须，可尝试放到 Native 实现
2）线程优化，尽可能减少 cpu 调度，具体就是控制线程数量和调度
3）在类加载的过程中通过 Hook 去掉类验证的过程，可以在 systrace 生成的文件中看到 verifyClass 过程，因为需要校验方法的每一个指令，所以是一个比较耗时的操作。
