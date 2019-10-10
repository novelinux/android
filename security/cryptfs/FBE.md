FBE
========================================

* 英文: FBE, File-Based Encryption
* Android官方文档: https://source.android.com/security/encryption/file-based.html
* 支持版本: Android 7.0支持
* 主要作用: Android 7.0及更高版本支持基于文件的加密(FBE). 基于文件的加密允许不同文件被不同keys加密并且解锁也是独立的.

```
Different from encrypting full disk, file based encryption can encrypt each file via different keys. The most noticeable usage
is that different user profile now using different keys. Previously, different users share the same disk encryption key.
OK, user/developer visible change is that we will have a so-called direct boot feature.
Previously after rebooting, a user has to unlock the entire disk (same password as the primary user), on a black screen with password
input, and then unlock his own profile (mostly primary user). Now, you can reboot directly to user profile unlock. No black screen
unlocking any more. For developer, you can run your app in user0 space which doesn't require user's profile unlocked.
That means your app can run before user input their password and swipe to unlock.
```

Direct Boot:
----------------------------------------

* Android官方文档: https://developer.android.com/training/articles/direct-boot.html#notification

当设备通电但用户尚未解锁设备时，Android 7.0以安全的直接引导模式(Direct boot)运行。为了支持这一点，系统提供了两个数据存储位置：

* 设备加密存储: 这是在直接引导模式期间和用户解锁设备后可用的存储位置。(/data/user_de/)
* 凭证加密存储: 它是默认存储位置，仅在用户解锁设备后可用。(/data/data --> /data/user/)

默认情况下，应用程序不会在直接引导模式下运行。如果您的应用程序需要在直接引导模式下采取行动，您可以注册应在此模式下运行的应用程序组件。在直接引导模式下需要运行的应用程序的一些常见用例包括：

* 1.已安排通知的应用程式，例如闹钟应用程式;
* 2.提供重要用户通知的应用，例如短信应用;
* 3.提供无障碍服务的应用，如Talkback.

如果您的应用程序需要在直接引导模式下运行时访问数据，请使用设备加密存储。设备加密存储包含使用仅在设备执行成功验证的引导后可用的密钥加密的数据。
对于应使用与用户凭据关联的密钥（如PIN或密码）加密的数据，请使用凭据加密存储。凭证加密存储仅在用户成功解锁设备后才可用，直到用户再次重新启动设备为止。
如果用户在解锁设备后启用锁定屏幕，则不会锁定凭证加密存储。

mount_all
----------------------------------------

```
/* mount_all <fstab> [ <path> ]*
 *
 * This function might request a reboot, in which case it will
 * not return.
 */
static int do_mount_all(const std::vector<std::string>& args) {
      ...
    } else if (ret == FS_MGR_MNTALL_DEV_FILE_ENCRYPTED) {
        if (e4crypt_install_keyring()) {
            return -1;
        }
        property_set("ro.crypto.state", "encrypted");
        property_set("ro.crypto.type", "file");

        // Although encrypted, we have device key, so we do not need to
        // do anything different from the nonencrypted case.
        ActionManager::GetInstance().QueueEventTrigger("nonencrypted");
    } else if (ret > 0) {
        ERROR("fs_mgr_mount_all returned unexpected error %d\n", ret);
    }
    /* else ... < 0: error */

    return ret;
}
```

FBE Key Generated
----------------------------------------

https://github.com/novelinux/android/blob/master/cryptfs/FBE_keys.md

init mkdir command
----------------------------------------

https://github.com/novelinux/android/blob/master/cryptfs/FBE_init_mkdir.md

FBE de directory prepare
----------------------------------------

https://github.com/novelinux/android/blob/master/cryptfs/FBE_de_dir_prep.md

FBE ce directory prepare
----------------------------------------

https://github.com/novelinux/android/blob/master/cryptfs/FBE_ce_dir_prep.md
