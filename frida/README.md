## Frida
https://frida.re/docs/android/

### Download & Install

#### Android
First off, download the latest frida-server for Android from our releases page and uncompress it.

```
$ unxz frida-server.xz
Now, let’s get it running on your device:
$ adb root # might be required
$ adb push frida-server /data/local/tmp/
$ adb shell "chmod 755 /data/local/tmp/frida-server"
$ adb shell "/data/local/tmp/frida-server &"
```

#### Mac

```
% pip install frida-tools
```

### Example

#### System call

```
% frida-trace -U -i open "com.ss.android.ugc.aweme"
Instrumenting...
open: Auto-generated handler at "/Users/Workspace/novelinux/frida-server/__handlers__/libutils.so/open.js"
Started tracing 1 function. Press Ctrl+C to stop.
           /* TID 0x537d */
  3888 ms  open(path="/data/user/0/com.ss.android.ugc.aweme/files/keva/repo/ss_location/ss_location.lxi", oflag=0x42)
  3898 ms  open(path="/data/user/0/com.ss.android.ugc.aweme/files/keva/repo/ss_location/ss_location.chk", oflag=0x42)
  3899 ms  open(path="/data/user/0/com.ss.android.ugc.aweme/files/keva/repo/ss_location/ss_location.blk", oflag=0x42)
  3899 ms
```

#### 自定义脚本

```
frida -U com.ss.android.ugc.aweme -l log_sql.js
```
