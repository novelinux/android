FBE
========================================

* 英文: FBE, File-Based Encryption
* Android官方文档: https://source.android.com/security/encryption/file-based.html
* 支持版本: Android 7.0支持
* 主要作用: Android 7.0及更高版本支持基于文件的加密(FBE). 基于文件的加密允许不同文件被不同keys加密并且解锁也是独立的.

```
Different from encrypting full disk, file based encryption can
encrypt each file via different keys. The most noticeable usage
is that different user profile now using different keys.
Previously, different users share the same disk encryption key.
OK, user/developer visible change is that we will have a so-called
direct boot feature.
Previously after rebooting, a user has to unlock the entire disk
(same password as the primary user), on a black screen with password
input, and then unlock his own profile (mostly primary user).
Now, you can reboot directly to user profile unlock. No black screen
unlocking any more. For developer, you can run your app in user0
space which doesn't require user's profile unlocked.
That means your app can run before user input their
password and swipe to unlock.
```

Direct Boot:
----------------------------------------

* Android官方文档: https://developer.android.com/training/articles/direct-boot.html#notification

当设备通电但用户尚未解锁设备时，Android 7.0以安全的直接引导模式(Direct boot)运行。为了支持这一点，系统提供了两个数据存储位置：

* 设备加密存储: 这是在直接引导模式期间和用户解锁设备后可用的存储位置。(/data/user_de/)
* 凭证加密存储: 它是默认存储位置，仅在用户解锁设备后可用。(/data/data --> /data/user/)

默认情况下，应用程序不会在直接引导模式下运行。如果您的应用程序需要在直接引导模式下采取行动，您可以注册应在此模式下运行的应用程序组件。在直接引导模式下需要运行的应用程序的一些常见用例包括：

1.已安排通知的应用程式，例如闹钟应用程式。
2.提供重要用户通知的应用，例如短信应用。
3. 提供无障碍服务的应用，如Talkback。

如果您的应用程序需要在直接引导模式下运行时访问数据，请使用设备加密存储。设备加密存储包含使用仅在设备执行成功验证的引导后可用的密钥加密的数据。
对于应使用与用户凭据关联的密钥（如PIN或密码）加密的数据，请使用凭据加密存储。凭证加密存储仅在用户成功解锁设备后才可用，直到用户再次重新启动设备为止。
如果用户在解锁设备后启用锁定屏幕，则不会锁定凭证加密存储。

Analysis
----------------------------------------

### 1.vold

#### LOG

```
01-15 06:48:24.514   592   598 E keymaster1_device: HwKmClose
01-15 06:48:24.514   592   598 D vold    : Created key /data/unencrypted/key
01-15 06:48:24.514   592   598 D vold    : Added key 123488388 (ext4:0e30728e813bbb1b) to keyring 1063057322 in process 592
01-15 06:48:24.515     1     1 I vdc     : 200 593 1
01-15 06:48:24.543   592   598 D vold    : e4crypt_init_user0
01-15 06:48:24.543   592   598 D vold    : Preparing: /data/misc/vold/user_keys
01-15 06:48:24.543   592   598 D vold    : Preparing: /data/misc/vold/user_keys/ce
01-15 06:48:24.544   592   598 D vold    : Preparing: /data/misc/vold/user_keys/de
01-15 06:48:24.544   592   598 D vold    : Preparing: /data/misc/vold/user_keys/ce/0
01-15 06:48:24.544   592   598 D vold    : Skipping non-key .
01-15 06:48:24.544   592   598 D vold    : Skipping non-key ..
01-15 06:48:24.553   592   598 E keymaster1_device: Keymaster Initialized
01-15 06:48:24.553   592   598 E keymaster1_device: TA API Major Verion: 2
01-15 06:48:24.553   592   598 E keymaster1_device: TA API Minor Verion: 0
01-15 06:48:24.553   592   598 E keymaster1_device: TA Major Verion: 2
01-15 06:48:24.553   592   598 E keymaster1_device: TA Minor Verion: 25
01-15 06:48:24.553   592   598 E keymaster1_device: set_version_req->flags: 1
01-15 06:48:24.554   592   598 D vold    : Creating key that doesn't need auth token

01-15 06:48:24.588   592   598 D vold    : Created key /data/misc/vold/user_keys/de/0
01-15 06:48:24.588   592   598 D vold    : Added key 1043496796 (ext4:5b317534021c2524) to keyring 1063057322 in process 592
01-15 06:48:24.588   592   598 D vold    : Added key 884894402 (ext4:5ebd155fc20b3ffa) to keyring 1063057322 in process 592
01-15 06:48:24.588   592   598 D vold    : Created keys for user 0
01-15 06:48:24.588   592   598 D vold    : Skipping non-de-key .
01-15 06:48:24.588   592   598 D vold    : Skipping non-de-key ..
01-15 06:48:24.588   592   598 D vold    : e4crypt_prepare_user_storage for volume null, user 0, serial 0, flags 1
01-15 06:48:24.588   592   598 D vold    : Preparing: /data/system/users/0
01-15 06:48:24.588   592   598 D vold    : Preparing: /data/misc/profiles/cur/0
01-15 06:48:24.588   592   598 D vold    : Preparing: /data/misc/profiles/cur/0/foreign-dex
01-15 06:48:24.589   592   598 D vold    : Preparing: /data/system_de/0
01-15 06:48:24.589   592   598 D vold    : Preparing: /data/misc_de/0
01-15 06:48:24.589   592   598 D vold    : Preparing: /data/user_de/0
01-15 06:48:24.589   592   598 I vold    : Policy for /data/system_de/0 set to 5b317534021c2524
01-15 06:48:24.589   592   598 I vold    : Policy for /data/misc_de/0 set to 5b317534021c2524
01-15 06:48:24.589   592   598 I vold    : Policy for /data/user_de/0 set to 5b317534021c2524
```

#### Call Stack

path: system/vold/Ext4Crypt.cpp

```
e4crypt_init_user0()
 |
 +-> create_and_install_user_keys (/data/misc/vold/user_keys)
 |
 +-> load_all_de_keys
 |
 +-> e4crypt_prepare_user_storage
 |   |
 |   +-> ensure_policy
 |       |
 |       +-> e4crypt_policy_ensure -> e4crypt_policy_set -> ioctl(fd, EXT4_IOC_SET_ENCRYPTION_POLICY, &eep);
 |
 +- non-FBE -> e4crypt_unlock_user_key(0, 0, "!", "!")
```

* https://github.com/novelinux/android/blob/master/cryptfs/DE_create.md

* /data/misc/vold/user_keys

```
sagit:/data/misc/vold/user_keys # ls -l de/0/
total 104
-rw------- 1 root root    92 1970-02-02 03:34 encrypted_key
-rw------- 1 root root   427 1970-02-02 03:34 keymaster_key_blob
-rw------- 1 root root 16384 1970-02-02 03:34 secdiscardable
-rw------- 1 root root    10 1970-02-02 03:34 stretching
-rw------- 1 root root     1 1970-02-02 03:34 version
sagit:/data/misc/vold/user_keys # ls -l ce/0/current/
total 104
-rw------- 1 root root    92 1970-02-02 03:34 encrypted_key
-rw------- 1 root root   427 1970-02-02 03:34 keymaster_key_blob
-rw------- 1 root root 16384 1970-02-02 03:34 secdiscardable
-rw------- 1 root root    10 1970-02-02 03:34 stretching
-rw------- 1 root root     1 1970-02-02 03:34 version
```

### 2.zygote

```
01-15 06:48:41.036   737   737 I Zygote  : ...preloaded 86 resources in 68ms.
01-15 06:48:41.037   738   738 I Zygote  : ...preloaded 86 resources in 63ms.
01-15 06:48:41.088   737   737 I Zygote  : ...preloaded 184 miui sdk resources in 51ms.
01-15 06:48:41.088   737   737 I Zygote  : ...preloaded 0 resources in 0ms.
...
01-15 06:48:41.205   737   737 I Zygote  : System server process 1639 has been created
01-15 06:48:41.206   737   737 I Zygote  : Accepting command socket connections
01-15 06:48:41.220   738   738 I Zygote  : Preloading shared libraries...
01-15 06:48:41.229   738   738 I Zygote  : Uninstalled ICU cache reference pinning...
01-15 06:48:41.230   738   738 I Zygote  : Installed AndroidKeyStoreProvider in 0ms.
01-15 06:48:41.234   738   738 I Zygote  : Warmed up JCA providers in 5ms.
01-15 06:48:41.235   738   738 D Zygote  : end preload
01-15 06:48:41.235   738   738 I art     : Starting a blocking GC Explicit
01-15 06:48:41.241   738   738 I art     : Explicit concurrent mark sweep GC freed 6717(379KB) AllocSpace objects, 0(0B) LOS objects, 39% free, 19MB/32MB, paused 84us total 6.470ms
01-15 06:48:41.241   738   738 I art     : Starting a blocking GC Explicit
01-15 06:48:41.247   738   738 I art     : Explicit concurrent mark sweep GC freed 628(18KB) AllocSpace objects, 0(0B) LOS objects, 40% free, 19MB/32MB, paused 81us total 5.661ms
01-15 06:48:41.248   738   738 I Zygote  : Accepting command socket connections
...
01-15 06:48:45.184  1722  1722 I dex2oat : dex2oat took 599.063ms (threads: 8) arena alloc=10MB (10773288B) java alloc=710KB (727664B) native alloc=5MB (5918520B) free=16MB (16888008B)
01-15 06:48:45.189  1639  1639 I InstallerConnection: disconnecting...
01-15 06:48:45.189   746   746 E         : eof
01-15 06:48:45.189   746   746 E         : failed to read size
01-15 06:48:45.189   746   746 I         : closing connection
```

### 3.system_server

```
01-15 06:48:45.196  1639  1639 I SystemServer: Entered the Android system server!
```

### 4.PackageManager

#### LOG

```
01-15 06:48:45.367  1639  1639 I SystemServer: StartPackageManagerService
...
01-15 06:48:58.541  1639  2808 I PackageManager: /system/framework/framework-ext-res changed; collecting certs
01-15 06:48:58.551  1639  2808 I chatty  : uid=1000(system) packagescan-1 expire 6 lines
01-15 06:48:58.553  1639  2808 W PackageManager: Permission android.permission.LOCATION_POLICY_INTERNAL from package com.miui.rom in an unknown group android.permission-group.LOCATION
01-15 06:48:58.556  1639  2809 I PackageManager: /system/framework/framework-res.apk changed; collecting certs
01-15 06:48:58.591  1639  2809 I PackageManager: /system/priv-app/CNEService changed; collecting certs
...
01-15 06:49:05.285  1639  2812 I PackageManager: /data/app/partner-BaiduMap changed; collecting certs
01-15 06:49:05.800  1639  2811 I PackageManager: /data/app/recommended-3rd-cn.wps.moffice_eng changed; collecting certs
...
01-15 06:49:08.026  1639  1639 V PackageManager: reconcileAppsData for null u0 0x1

01-15 06:49:08.027   746   746 I SELinux : SELinux: Loaded file_contexts contexts from /file_contexts.bin.
01-15 06:49:08.028   746   746 D installd: Detected label change from u:object_r:system_data_file:s0 to u:object_r:app_data_file:s0:c512,c768 at /data/user_de/0/com.android.cts.priv.ctsshim; running recursive restorecon
...
01-15 06:49:08.213   746   746 D installd: Detected label change from u:object_r:system_data_file:s0 to u:object_r:radio_data_file:s0 at /data/user_de/0/com.qti.editnumber; running recursive restorecon
01-15 06:49:08.214  1639  1639 V PackageManager: reconcileAppsData finished 214 packages

...

01-15 06:49:08.388  2816  2816 I dex2oat : /system/bin/dex2oat --compiler-filter=speed
01-15 06:50:36.703  4878  4878 I dex2oat : dex2oat took 36.659ms (threads: 8) arena alloc=9KB (9760B) java alloc=115KB (118304B) native alloc=1087KB (1113648B) free=2MB (2556368B)
01-15 06:50:36.706  1639  1639 W PackageManager: No disk maintenance in 1205436706; running immediately
01-15 06:50:36.706  1639  1639 I SystemServer: StartLockSettingsService
01-15 06:50:36.706  1639  3247 I MountService: Running fstrim idle maintenance
01-15 06:50:36.706  1639  1639 I SystemServiceManager: Starting com.android.server.LockSettingsService$Lifecycle
01-15 06:50:36.706  1639  3247 D VoldConnector: SND -> {3 fstrim dotrim}
01-15 06:50:36.707  1639  3248 D VoldConnector: RCV <- {200 3 Command succeeded}
01-15 06:50:36.707   592  4887 D vold    : Starting trim of /data
```

#### SOURCES

```
PackageManagerService
 |
 +-> reconcileAppsDataLI +-> prepareAppDataLeafLIF -> mInstaller.createAppData -> create_app_data(installd)
     |                   |
     +-> prepareAppDataLIF  <-+
     |                        |
     +-> maybeMigrateAppDataLIF
```

### 5.vold

#### LOG

```
01-15 06:50:41.581  1639  1755 I ActivityManager: Start proc 5566:com.android.defcontainer/u0a9 for on-hold
01-15 06:50:41.582  1639  1755 D ActivityManager: Finishing user boot 0
...
01-15 06:50:41.585  1639  1755 D CryptdConnector: SND -> {2 cryptfs unlock_user_key 0 0 [scrubbed] [scrubbed]}
01-15 06:50:41.586   592   598 D vold    : e4crypt_unlock_user_key 0 serial=0 token_present=0
01-15 06:50:41.586   592   598 W vold    : Tried to unlock already-unlocked key for user 0
...
01-15 06:50:41.586  1639  3249 D CryptdConnector: RCV <- {200 2 Command succeeded}
01-15 06:50:41.587  1639  1755 D CryptdConnector: SND -> {3 cryptfs prepare_user_storage ! 0 0 2}
01-15 06:50:41.587   592   598 D vold    : e4crypt_prepare_user_storage for volume null, user 0, serial 0, flags 2
01-15 06:50:41.587   592   598 D vold    : Preparing: /data/system_ce/0
01-15 06:50:41.587   592   598 D vold    : Preparing: /data/misc_ce/0
01-15 06:50:41.587   592   598 D vold    : Preparing: /data/media/0
01-15 06:50:41.588   592   598 D vold    : Preparing: /data/data
01-15 06:50:41.630   592   598 I vold    : Policy for /data/system_ce/0 set to 5ebd155fc20b3ffa
01-15 06:50:41.630   592   598 I vold    : Policy for /data/misc_ce/0 set to 5ebd155fc20b3ffa
01-15 06:50:41.631   592   598 I vold    : Policy for /data/media/0 set to 5ebd155fc20b3ffa
01-15 06:50:41.631   592   598 I vold    : Policy for /data/data set to 5ebd155fc20b3ffa
01-15 06:50:41.631   592   598 V vold    : Starting restorecon of /data/system_ce/0
01-15 06:50:41.635   592   598 V vold    : Finished restorecon of /data/system_ce/0
01-15 06:50:41.635   592   598 V vold    : Starting restorecon of /data/misc_ce/0
01-15 06:50:41.636   592   598 V vold    : Finished restorecon of /data/misc_ce/0
01-15 06:50:41.637  1639  3249 D CryptdConnector: RCV <- {200 5 Command succeeded}
```

#### Call Stack

```
ActivityManagerService.systemReady
 |
UserController.startUser
 |
UserController.finishUserBoot
 |
UserController.maybeUnlockUser
 |
UserController.unlockUserCleared
 |
 +-> MountService.unlockUserKey
 |   |
 |   +-> e4crypt_unlock_user_key
 |
UserController.finishUserUnlocking
 |
UserManagerService.onBeforeUnlockUser
 |
PackageManagerService.prepareUserData
 |
PackageManagerService.prepareUserDataLI
 |
MountService.prepareUserStorage
 |
 +-> e4crypt_prepare_user_storage
```

### 6.UserManagerService

```
01-15 06:50:41.638  1639  1755 V UserManagerService: Found /data/user/0 with serial number -1
01-15 06:50:41.638  1639  1755 D UserManagerService: Serial number missing on /data/user/0; assuming current is valid
01-15 06:50:41.638  1639  1755 V UserManagerService: Found /data/system_ce/0 with serial number -1
01-15 06:50:41.638  1639  1755 D UserManagerService: Serial number missing on /data/system_ce/0; assuming current is valid
```

### 7.PackageManager

```
01-15 06:50:41.639  1639  1755 V PackageManager: reconcileAppsData for null u0 0x2
...
01-15 06:50:42.037   746   746 D installd: Detected label change from u:object_r:system_data_file:s0 to u:object_r:radio_data_file:s0 at /data/data/com.qti.editnumber; running recursive restorecon
01-15 06:50:42.038  1639  1755 V PackageManager: reconcileAppsData finished 214 packages
```