# systrace

## ## show current window

```
adb shell dumpsys window | grep mCurrentFocus
```

## command

```
./systrace.py -t 20 gfx input view webview wm am res sched freq idle disk  -a com.google.android.wearable.app
```
