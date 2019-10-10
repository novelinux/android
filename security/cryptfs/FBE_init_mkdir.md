FBE the command "mkdir" of init scripts
========================================

* LOGS

```
[    5.612948] ext4_utils: Setting 3fcbc19c policy on /data/bootchart!
[    5.616033] ext4_utils: Setting 3fcbc19c policy on /data/misc!
[    5.623805] ext4_utils: Setting 3fcbc19c policy on /data/local!
[    5.629432] ext4_utils: Setting 3fcbc19c policy on /data/app-private!
[    5.630260] ext4_utils: Setting 3fcbc19c policy on /data/app-ephemeral!
[    5.631094] ext4_utils: Setting 3fcbc19c policy on /data/app-asec!
[    5.631901] ext4_utils: Setting 3fcbc19c policy on /data/app-lib!
[    5.633430] ext4_utils: Setting 3fcbc19c policy on /data/app!
```

mkdir
----------------------------------------

### do_mkdir

path: system/core/init/builtins.cpp
```
static int do_mkdir(const std::vector<std::string>& args) {
    mode_t mode = 0755;
    int ret;

    /* mkdir <path> [mode] [owner] [group] */

    if (args.size() >= 3) {
        mode = std::stoul(args[2], 0, 8);
    }

    ret = make_dir(args[1].c_str(), mode);
    /* chmod in case the directory already exists */
    if (ret == -1 && errno == EEXIST) {
        ret = fchmodat(AT_FDCWD, args[1].c_str(), mode, AT_SYMLINK_NOFOLLOW);
    }
    if (ret == -1) {
        return -errno;
    }

    if (args.size() >= 4) {
        uid_t uid = decode_uid(args[3].c_str());
        gid_t gid = -1;

        if (args.size() == 5) {
            gid = decode_uid(args[4].c_str());
        }

        if (lchown(args[1].c_str(), uid, gid) == -1) {
            return -errno;
        }

        /* chown may have cleared S_ISUID and S_ISGID, chmod again */
        if (mode & (S_ISUID | S_ISGID)) {
            ret = fchmodat(AT_FDCWD, args[1].c_str(), mode, AT_SYMLINK_NOFOLLOW);
            if (ret == -1) {
                return -errno;
            }
        }
    }

    if (e4crypt_is_native()) {
        if (e4crypt_set_directory_policy(args[1].c_str())) {
            wipe_data_via_recovery(std::string() + "set_policy_failed:" + args[1]);
            return -1;
        }
    }
    return 0;
}
```

### e4crypt_is_native

path: path: system/extras/ext4_utils/ext4_crypt.cpp
```
bool e4crypt_is_native() {
    char value[PROPERTY_VALUE_MAX];
    property_get("ro.crypto.type", value, "none");
    return !strcmp(value, "file");
}
```

### e4crypt_set_directory_policy

path: system/extras/ext4_utils/ext4_crypt_init_extensions.cpp
```
int e4crypt_set_directory_policy(const char* dir)
{
    init_logging();

    // Only set policy on first level /data directories
    // To make this less restrictive, consider using a policy file.
    // However this is overkill for as long as the policy is simply
    // to apply a global policy to all /data folders created via makedir
    if (!dir || strncmp(dir, "/data/", 6) || strchr(dir + 6, '/')) {
        return 0;
    }

    // Special case various directories that must not be encrypted,
    // often because their subdirectories must be encrypted.
    // This isn't a nice way to do this, see b/26641735
    std::vector<std::string> directories_to_exclude = {
        "lost+found",
        "system_ce", "system_de",
        "misc_ce", "misc_de",
        "media",
        "data", "user", "user_de",
    };
    std::string prefix = "/data/";
    for (auto d: directories_to_exclude) {
        if ((prefix + d) == dir) {
            KLOG_INFO(TAG, "Not setting policy on %s\n", dir);
            return 0;
        }
    }

    std::string ref_filename = std::string("/data") + e4crypt_key_ref;
    std::string policy;
    if (!android::base::ReadFileToString(ref_filename, &policy)) {
        KLOG_ERROR(TAG, "Unable to read system policy to set on %s\n", dir);
        return -1;
    }
    KLOG_INFO(TAG, "Setting policy on %s\n", dir);
    int result = e4crypt_policy_ensure(dir, policy.c_str(), policy.size());
    if (result) {
        KLOG_ERROR(TAG, "Setting %02x%02x%02x%02x policy on %s failed!\n",
                   policy[0], policy[1], policy[2], policy[3], dir);
        return -1;
    }

    return 0;
}
```

* policy - "/data/unencrypted/ref"

https://github.com/novelinux/android/blob/master/cryptfs/FBE_keys.md

e4crypt_policy_ensure
----------------------------------------

https://github.com/novelinux/android/blob/master/cryptfs/e4crypt_policy_ensure.md
