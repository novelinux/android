FBE "de" directory prepare
========================================

* LOGS

```
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

* flowchart

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

init_user0
----------------------------------------

path: system/core/init/builtins.cpp
```
static int do_init_user0(const std::vector<std::string>& args) {
    return e4crypt_do_init_user0();
}
```

### 1.e4crypt_do_init_user0

path: system/extras/ext4_utils/ext4_crypt_init_extensions.cpp
```
int e4crypt_do_init_user0()
{
    init_logging();

    const char* argv[] = { "/system/bin/vdc", "--wait", "cryptfs", "init_user0" };
    int rc = android_fork_execvp(4, (char**) argv, NULL, false, true);
    LOG(INFO) << "init_user0 result: " << rc;
    return rc;
}
```

### 2.e4crypt_init_user0

path: system/vold/Ext4Crypt.cpp
```
bool e4crypt_init_user0() {
    LOG(DEBUG) << "e4crypt_init_user0";
    if (e4crypt_is_native()) {
        if (!prepare_dir(user_key_dir, 0700, AID_ROOT, AID_ROOT)) return false;
        if (!prepare_dir(user_key_dir + "/ce", 0700, AID_ROOT, AID_ROOT)) return false;
        if (!prepare_dir(user_key_dir + "/de", 0700, AID_ROOT, AID_ROOT)) return false;
        if (!path_exists(get_de_key_path(0))) {
            if (!create_and_install_user_keys(0, false)) return false;
        }
        // TODO: switch to loading only DE_0 here once framework makes
        // explicit calls to install DE keys for secondary users
        if (!load_all_de_keys()) return false;
    }
    // We can only safely prepare DE storage here, since CE keys are probably
    // entangled with user credentials.  The framework will always prepare CE
    // storage once CE keys are installed.
    if (!e4crypt_prepare_user_storage(nullptr, 0, 0, FLAG_STORAGE_DE)) {
        LOG(ERROR) << "Failed to prepare user 0 storage";
        return false;
    }

    // If this is a non-FBE device that recently left an emulated mode,
    // restore user data directories to known-good state.
    if (!e4crypt_is_native() && !e4crypt_is_emulated()) {
        e4crypt_unlock_user_key(0, 0, "!", "!");
    }

    return true;
}
```

### 3.e4crypt_prepare_user_storage

path: system/vold/Ext4Crypt.cpp
```
bool e4crypt_prepare_user_storage(const char* volume_uuid, userid_t user_id, int serial,
        int flags) {
    LOG(DEBUG) << "e4crypt_prepare_user_storage for volume " << escape_null(volume_uuid)
               << ", user " << user_id << ", serial " << serial << ", flags " << flags;

    if (flags & FLAG_STORAGE_DE) {
        // DE_sys key
        auto system_legacy_path = android::vold::BuildDataSystemLegacyPath(user_id);
        auto misc_legacy_path = android::vold::BuildDataMiscLegacyPath(user_id);
        auto profiles_de_path = android::vold::BuildDataProfilesDePath(user_id);
        auto foreign_de_path = android::vold::BuildDataProfilesForeignDexDePath(user_id);

        // DE_n key
        auto system_de_path = android::vold::BuildDataSystemDePath(user_id);
        auto misc_de_path = android::vold::BuildDataMiscDePath(user_id);
        auto user_de_path = android::vold::BuildDataUserDePath(volume_uuid, user_id);

        if (!prepare_dir(system_legacy_path, 0700, AID_SYSTEM, AID_SYSTEM)) return false;
#if MANAGE_MISC_DIRS
        if (!prepare_dir(misc_legacy_path, 0750, multiuser_get_uid(user_id, AID_SYSTEM),
                multiuser_get_uid(user_id, AID_EVERYBODY))) return false;
#endif
        if (!prepare_dir(profiles_de_path, 0771, AID_SYSTEM, AID_SYSTEM)) return false;
        if (!prepare_dir(foreign_de_path, 0773, AID_SYSTEM, AID_SYSTEM)) return false;

        if (!prepare_dir(system_de_path, 0770, AID_SYSTEM, AID_SYSTEM)) return false;
        if (!prepare_dir(misc_de_path, 01771, AID_SYSTEM, AID_MISC)) return false;
        if (!prepare_dir(user_de_path, 0771, AID_SYSTEM, AID_SYSTEM)) return false;

        // For now, FBE is only supported on internal storage
        if (e4crypt_is_native() && volume_uuid == nullptr) {
            std::string de_raw_ref;
            if (!lookup_key_ref(s_de_key_raw_refs, user_id, &de_raw_ref)) return false;
            if (!ensure_policy(de_raw_ref, system_de_path)) return false;
            if (!ensure_policy(de_raw_ref, misc_de_path)) return false;
            if (!ensure_policy(de_raw_ref, user_de_path)) return false;
        }
    }

    if (flags & FLAG_STORAGE_CE) {
        // CE_n key
        auto system_ce_path = android::vold::BuildDataSystemCePath(user_id);
        auto misc_ce_path = android::vold::BuildDataMiscCePath(user_id);
        auto media_ce_path = android::vold::BuildDataMediaCePath(volume_uuid, user_id);
        auto user_ce_path = android::vold::BuildDataUserCePath(volume_uuid, user_id);

        if (!prepare_dir(system_ce_path, 0770, AID_SYSTEM, AID_SYSTEM)) return false;
        if (!prepare_dir(misc_ce_path, 01771, AID_SYSTEM, AID_MISC)) return false;
        if (!prepare_dir(media_ce_path, 0770, AID_MEDIA_RW, AID_MEDIA_RW)) return false;
        if (!prepare_dir(user_ce_path, 0771, AID_SYSTEM, AID_SYSTEM)) return false;

        // For now, FBE is only supported on internal storage
        if (e4crypt_is_native() && volume_uuid == nullptr) {
            std::string ce_raw_ref;
            if (!lookup_key_ref(s_ce_key_raw_refs, user_id, &ce_raw_ref)) return false;
            if (!ensure_policy(ce_raw_ref, system_ce_path)) return false;
            if (!ensure_policy(ce_raw_ref, misc_ce_path)) return false;
            if (!ensure_policy(ce_raw_ref, media_ce_path)) return false;
            if (!ensure_policy(ce_raw_ref, user_ce_path)) return false;

            // Now that credentials have been installed, we can run restorecon
            // over these paths
            // NOTE: these paths need to be kept in sync with libselinux
            android::vold::RestoreconRecursive(system_ce_path);
            android::vold::RestoreconRecursive(misc_ce_path);
        }
    }

    return true;
}
```

### lookup_key_ref

path: system/vold/Ext4Crypt.cpp
```
static bool lookup_key_ref(const std::map<userid_t, std::string>& key_map, userid_t user_id,
                           std::string* raw_ref) {
    auto refi = key_map.find(user_id);
    if (refi == key_map.end()) {
        LOG(ERROR) << "Cannot find key for " << user_id;
        return false;
    }
    *raw_ref = refi->second;
    return true;
}
```

### ensure_policy

path: system/vold/Ext4Crypt.cpp
```
static bool ensure_policy(const std::string& raw_ref, const std::string& path) {
    if (e4crypt_policy_ensure(path.c_str(),
                              raw_ref.data(), raw_ref.size(),
                              cryptfs_get_file_encryption_mode()) != 0) {
        LOG(ERROR) << "Failed to set policy on: " << path;
        return false;
    }
    return true;
}
```

e4crypt_policy_ensure
----------------------------------------

https://github.com/novelinux/android/blob/master/cryptfs/e4crypt_policy_ensure.md

reconcileAppsData
----------------------------------------

* LOG

```
01-15 06:48:45.367  1639  1639 I SystemServer: StartPackageManagerService
...
01-15 06:48:58.591  1639  2809 I PackageManager: /system/priv-app/CNEService changed; collecting certs
...
01-15 06:49:05.285  1639  2812 I PackageManager: /data/app/partner-BaiduMap changed; collecting certs

01-15 06:49:08.026  1639  1639 V PackageManager: reconcileAppsData for null u0 0x1

01-15 06:49:08.027   746   746 I SELinux : SELinux: Loaded file_contexts contexts from /file_contexts.bin.
01-15 06:49:08.028   746   746 D installd: Detected label change from u:object_r:system_data_file:s0 to u:object_r:app_data_file:s0:c512,c768 at /data/user_de/0/com.android.cts.priv.ctsshim; running recursive restorecon
...
01-15 06:49:08.213   746   746 D installd: Detected label change from u:object_r:system_data_file:s0 to u:object_r:radio_data_file:s0 at /data/user_de/0/com.qti.editnumber; running recursive restorecon
01-15 06:49:08.214  1639  1639 V PackageManager: reconcileAppsData finished 214 packages
...

```

* Flowchart

```
PackageManagerService
 |
 +-> reconcileAppsDataLI +-> prepareAppDataLeafLIF -> mInstaller.createAppData -> create_app_data(installd)
     |                   |
     +-> prepareAppDataLIF  <-+
     |                        |
     +-> maybeMigrateAppDataLIF
```
