## IO

1.关闭预读

```
echo 0 > /sys/block/sda/queue/read_ahead_kb
echo 0 > /sys/block/dm-0/queue/read_ahead_kb
echo 0 > /sys/block/dm-1/queue/read_ahead_kb
```

2.IO详情Profile

精确到文件名，offset，size，按序列记录
方法：

1. adb shell "echo 1 > /d/tracing/events/android_fs/enable"
2. 抓取systrace
3. 将android_fs内IO相关的events转化为systrace可以可视化的格式，使用如下python脚本，在对应IO wait的地方展示详细信息(只包含read的转换)

3. read序列生成：

```
adb shell "cmd package compile -m speed-profile <package-name>"
adb shell "echo 1 > /d/tracing/events/android_fs/enable"
adb shell "am force-stop <package-name>"
adb shell "echo 0 > /proc/sys/vm/drop_caches"
```

A.启动systrace
B.启动App
C.生成systrace

启动过程中的read序列抽取出来写入新的文件，如下是启动过程中的read序列

4.strace

```
setprop wrap.com.zhiliaoapp.musically "logwrapper strace -f -e trace=read,write, -T -tt -ff -o /data/local/tmp/stracedir/strace.txt"
```

5.focus window

```
adb shell dumpsys window | grep mCurrentFocus
  mCurrentFocus=Window{1a71128 u0 com.zhiliaoapp.musically/com.ss.android.ugc.aweme.splash.SplashActivity}
```

6.compiler

```
adb shell cmd package compile -m speed-profile -f <package-name>
```

7.systrace

```
systrace -a <package-name> -t 6
``