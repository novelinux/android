FBE "ce" directory prepare
========================================

* LOG

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

* Flowchart

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

reconcileAppsData
----------------------------------------

```
01-15 06:50:41.639  1639  1755 V PackageManager: reconcileAppsData for null u0 0x2
...
01-15 06:50:42.037   746   746 D installd: Detected label change from u:object_r:system_data_file:s0 to u:object_r:radio_data_file:s0 at /data/data/com.qti.editnumber; running recursive restorecon
01-15 06:50:42.038  1639  1755 V PackageManager: reconcileAppsData finished 214 packages
```