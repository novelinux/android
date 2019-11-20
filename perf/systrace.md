# systrace

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
