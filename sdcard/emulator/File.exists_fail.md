
## 背景

systemui进程通过File.exists函数判断/storage/emulated/0/MIUI/ringtone目录下铃声文件返回ENOENT错误

## 分析

ENOENT No such file or directory (POSIX.1)
通过查看错误码得知对应错误是说明对应文件不存在,于是我们通过ls -l命令看下对应sdcard目录下是否有铃声文件

```
:/storage/emulated/0/MIUI/ringtone # ls -l
total 1576
-rw-rw---- 1 root sdcard_rw 799097 2017-07-17 15:11 薛之谦&王栎鑫 - 等我回家 (Live)_&_2762c729-2007-4bc7-896c-b4867be07d62.mp3
```

我们看到对应文件在模拟sdcard上是真实存在的,于是我们怀疑是不是权限的问题呢?于是查看下systemui的权限如下:

A.分析UID/GID

```
# ps | grep systemui
system    4757  810   2443292 167192 SyS_epoll_ 7f88996230 S com.android.systemui
/proc/4757 # cat status                                                                                                                                                                                      
Name:	ndroid.systemui
...
Uid:	1000	1000	1000	1000
Gid:	1000	1000	1000	1000
Ngid:	0
FDSize:	256
Groups:	1000 1007 1010 1015 1021 1023 2001 2002 3001 3002 3003 3006 3007 3009 9997 41000
```

通过查看system/core/include/private/androidfilesystemconfig.h看到sdcard_rw对应的id是1015,而systemui进程属于1015组,默认有访问对应文件的权限.

```
#define AID_SDCARD_RW     1015  /* external storage write access */
```

B.分析selinux
通过命令setenforce 0关闭selinux,发现问题依旧存在

通过上述两步分析可以确认排除权限问题导致无法访问对应文件

C.写一个测试APP
写了一个测试APP去访问对应文件发现也能访问,就是systemui不行

D.查看对应进程的mnt namespace
因为我们通过adb root && adb shell进去看到的文件是shell进程看到的挂载点,并不是SystemUI进程看到的,我们现在需要确认是否是不同进程采用了不同的mnt namespace.

```
Systemui:
# ps 
USER      PID   PPID  VSIZE  RSS   WCHAN              PC  NAME
system    4757  810   2443292 167272 SyS_epoll_ 7f88996230 S com.android.systemui

# ls -l /proc/4757/ns/                                                                                                                                                                                     
total 0
lrwxrwxrwx 1 system system 0 2017-07-20 14:43 mnt -> mnt:[4026533876]
lrwxrwxrwx 1 system system 0 2017-07-20 14:43 net -> net:[4026531893]
# ls -l /proc/810/ns/                                                                                                                                                                                      
total 0
lrwxrwxrwx 1 root root 0 2017-07-20 14:43 mnt -> mnt:[4026533835]
lrwxrwxrwx 1 root root 0 2017-07-20 14:43 net -> net:[4026531893]
```

通过proc文件系统我们看到确实systemui的mnt namespace和其父进程的mnt namespace是不一样的,这就说明Android系统通过zygote fork出来的每一个应用程序有可能其对应的mnt namespace是不一样的,我们查看对应的代码如下:

```
ForkAndSpecializeCommon
xref: /v8-n-jason-dev/frameworks/base/core/jni/comandroidinternalosZygote.cpp

443// Utility routine to fork zygote and specialize the child process.
444static pid_t ForkAndSpecializeCommon(JNIEnv* env, uid_t uid, gid_t gid, jintArray javaGids,
445                                     jint debug_flags, jobjectArray javaRlimits,
446                                     jlong permittedCapabilities, jlong effectiveCapabilities,
447                                     jint mount_external,
448                                     jstring java_se_info, jstring java_se_name,
449                                     bool is_system_server, jintArray fdsToClose,
450                                     jstring instructionSet, jstring dataDir) {
     ...
487  pid_t pid = fork();
488
489  if (pid == 0) {
490    // The child process.
491    gMallocLeakZygoteChild = 1;
       ...
528
529    if (!MountEmulatedStorage(uid, mount_external, use_native_bridge)) {
530      ALOGW("Failed to mount emulated storage: %s", strerror(errno));
531      if (errno == ENOTCONN || errno == EROFS) {
532        // When device is actively encrypting, we get ENOTCONN here
533        // since FUSE was mounted before the framework restarted.
534        // When encrypted device is booting, we get EROFS since
535        // FUSE hasn't been created yet by init.
536        // In either case, continue without external storage.
537      } else {
538        RuntimeAbort(env, __LINE__, "Cannot continue without emulated storage");
539      }
540    }
541
MountEmulatedStorage
296// Create a private mount namespace and bind mount appropriate emulated
297// storage for the given user.
298static bool MountEmulatedStorage(uid_t uid, jint mount_mode,
299        bool force_mount_namespace) {
300    // See storage config details at http://source.android.com/tech/storage/
301
302    // Create a second private mount namespace for our process
303    if (unshare(CLONE_NEWNS) == -1) {
304        ALOGW("Failed to unshare(): %s", strerror(errno));
305        return false;
306    }
307
308    String8 storageSource;
309    if (mount_mode == MOUNT_EXTERNAL_DEFAULT) {
310        storageSource = "/mnt/runtime/default";
311    } else if (mount_mode == MOUNT_EXTERNAL_READ) {
312        storageSource = "/mnt/runtime/read";
313    } else if (mount_mode == MOUNT_EXTERNAL_WRITE) {
314        storageSource = "/mnt/runtime/write";
315    } else {
316        // Sane default of no storage visible
317        return true;
318    }
319    if (TEMP_FAILURE_RETRY(mount(storageSource.string(), "/storage",
320            NULL, MS_BIND | MS_REC | MS_SLAVE, NULL)) == -1) {
321        ALOGW("Failed to mount %s to /storage: %s", storageSource.string(), strerror(errno));
322        return false;
323    }
324
325    // Mount user-specific symlink helper into place
326    userid_t user_id = multiuser_get_user_id(uid);
327    const String8 userSource(String8::format("/mnt/user/%d", user_id));
328    if (fs_prepare_dir(userSource.string(), 0751, 0, 0) == -1) {
329        return false;
330    }
331    if (TEMP_FAILURE_RETRY(mount(userSource.string(), "/storage/self",
332            NULL, MS_BIND, NULL)) == -1) {
333        ALOGW("Failed to mount %s to /storage/self: %s", userSource.string(), strerror(errno));
334        return false;
335    }
336
337    return true;
338}
```

其中303行unshare代码就是告诉新fork出来的子进程使用新的mnt namespace,从代码上我们也验证了Android系统每一个子进程都是使用不同的mnt namespace,

于是我们看下对应SystemUI进程的/proc/pid/mounts中看下其模拟sdcard的挂载情况:

```
/dev/fuse /mnt/runtime/default/emulated fuse rw,nosuid,nodev,noexec,noatime,user_id=1023,group_id=1023,default_permissions,allow_other 0 0
/dev/fuse /mnt/runtime/read/emulated fuse rw,nosuid,nodev,noexec,noatime,user_id=1023,group_id=1023,default_permissions,allow_other 0 0
/dev/fuse /mnt/runtime/write/emulated fuse rw,nosuid,nodev,noexec,noatime,user_id=1023,group_id=1023,default_permissions,allow_other 0 0
```

 我们发现systemui进程只有如下三个挂载点,看不到对应的/storage/emulated,这就能解释为什么会报ENONENT错误了,我们看下正常能够访问对应文件的

mounts看下其模拟sdcard的挂载情况:

```
/dev/fuse /mnt/runtime/default/emulated fuse rw,nosuid,nodev,noexec,noatime,user_id=1023,group_id=1023,default_permissions,allow_other 0 0
/dev/fuse /mnt/runtime/read/emulated fuse rw,nosuid,nodev,noexec,noatime,user_id=1023,group_id=1023,default_permissions,allow_other 0 0
/dev/fuse /mnt/runtime/write/emulated fuse rw,nosuid,nodev,noexec,noatime,user_id=1023,group_id=1023,default_permissions,allow_other 0 0

/dev/fuse /storage/emulated fuse rw,nosuid,nodev,noexec,noatime,user_id=1023,group_id=1023,default_permissions,allow_other 0 0
```

果真,我们发现多了一个/storage/emulated挂载点

于是我们思考,这个挂载点是哪里来的呢?我们再看下对应MountEmulatedStorage函数,其会根据mount_mode参数来决定将/mnt/runtime/下的default, read, write其中一个挂载点

再次挂载为/storage/emulated,于是我们想只要找到systemui进程的mount_mode参数是哪里来的不久能够解释其为什么会报这个诡异的错误了么? 于是我们在AMS中发现:

startProcessLocked
```
3837            int mountExternal = Zygote.MOUNT_EXTERNAL_NONE;
3838            if (!app.isolated) {
3839                int[] permGids = null;
3840                try {
3841                    checkTime(startTime, "startProcess: getting gids from package manager");
3842                    final IPackageManager pm = AppGlobals.getPackageManager();
3843                    permGids = pm.getPackageGids(app.info.packageName,
3844                            MATCH_DEBUG_TRIAGED_MISSING, app.userId);
3845                    MountServiceInternal mountServiceInternal = LocalServices.getService(
3846                            MountServiceInternal.class);
3847                    mountExternal = mountServiceInternal.getExternalStorageMountMode(uid,
3848                            app.info.packageName);
3849                } catch (RemoteException e) {
3850                    throw e.rethrowAsRuntimeException();
3851                }
3852

3934            checkTime(startTime, "startProcess: asking zygote to start proc");
3935            Process.ProcessStartResult startResult = Process.start(entryPoint,
3936                    app.processName, uid, uid, gids, debugFlags, mountExternal,
3937                    app.info.targetSdkVersion, app.info.seinfo, requiredAbi, instructionSet,
3938                    app.info.dataDir, entryPointArgs);
```

应用程序进程的创建都是通过AMS的startProcessLocked通过啊socket向Zygote进程发送新进程信息,其中mountExternal参数表明需要为新进程创建选取模拟sdcard的挂载点,
默认是Zygote.MOUNT_EXTERNAL_NONE,如果是非isolated进程则通过函数mountServiceInternal.getExternalStorageMountMode去获取:

getExternalStorageMountMode
```
3880        @Override
3881        public int getExternalStorageMountMode(int uid, String packageName) {
3882            // No locking - CopyOnWriteArrayList
3883            int mountMode = Integer.MAX_VALUE;
3884            for (ExternalStorageMountPolicy policy : mPolicies) {
3885                final int policyMode = policy.getMountMode(uid, packageName);
3886                if (policyMode == Zygote.MOUNT_EXTERNAL_NONE) {
3887                    return Zygote.MOUNT_EXTERNAL_NONE;
3888                }
3889                mountMode = Math.min(mountMode, policyMode);
3890            }
3891            if (mountMode == Integer.MAX_VALUE) {
3892                return Zygote.MOUNT_EXTERNAL_NONE;
3893            }
3894            return mountMode;
3895        }
```

应用程序使用模拟sdcard的policy是保存到mPolicies变量中的,需要的使用通过policy的getMountMode获取,其是在PMS systemReady时添加的,如下所示:

systemReady

```
18634    @Override
18635    public void systemReady() {

18727
18728        mInstallerService.systemReady();
18729        mPackageDexOptimizer.systemReady();
18730
18731        MountServiceInternal mountServiceInternal = LocalServices.getService(
18732                MountServiceInternal.class);
18733        mountServiceInternal.addExternalStoragePolicy(
18734                new MountServiceInternal.ExternalStorageMountPolicy() {
18735            @Override
18736            public int getMountMode(int uid, String packageName) {
18737                if (Process.isIsolated(uid)) {
18738                    return Zygote.MOUNT_EXTERNAL_NONE;
18739                }
18740                if (checkUidPermission(WRITE_MEDIA_STORAGE, uid) == PERMISSION_GRANTED) {
18741                    return Zygote.MOUNT_EXTERNAL_DEFAULT;
18742                }
18743                if (checkUidPermission(READ_EXTERNAL_STORAGE, uid) == PERMISSION_DENIED) {
18744                    return Zygote.MOUNT_EXTERNAL_DEFAULT;
18745                }
18746                if (checkUidPermission(WRITE_EXTERNAL_STORAGE, uid) == PERMISSION_DENIED) {
18747                    return Zygote.MOUNT_EXTERNAL_READ;
18748                }
18749                return Zygote.MOUNT_EXTERNAL_WRITE;
18750            }
18751
18752            @Override
18753            public boolean hasExternalStorage(int uid, String packageName) {
18754                return true;
18755            }
18756        });
18757
```

在AMS对应getExternalStorageMountMode前后查看对应systemui启动时的LOG,发现systemui并没有返回默认的Zygote.MOUNT_EXTERNAL_WRITE,而是NONE,
通过LOG发现systemui在system还没有完全ready被唤起来了,对应的policy完全没有被初始化

```
07-20 10:59:50.284  1635  1635 I ActivityManager: Start proc 2060:com.android.systemui/1000 for service com.android.keyguard/.KeyguardService

...

07-20 10:59:50.458 1635 1635 I ActivityManager: System now ready
07-20 10:59:50.461 1635 1635 I SystemServer: Making services ready
```

而我们看到正常的systemui启动过程如下:

```
07-20 11:09:33.006 1557 1557 I ActivityManager: System now ready
07-20 11:09:33.008 1557 1557 I SystemServer: Making services ready

...

07-20 11:09:33.204  1557  1557 I ActivityManager: Start proc 2185:com.android.systemui/1000 for service com.android.systemui/.SystemUIService
```

结论
根本原因是systemui进程被com.android.keyguard/.KeyguardService过早唤醒,此时系统尚未ready
