FBE Bootchart
========================================

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

init.rc
----------------------------------------

```
on post-fs-data
    # We chown/chmod /data again so because mount is run as root + defaults
    chown system system /data
    chmod 0771 /data
    # We restorecon /data in case the userdata partition has been reset.
    restorecon /data

    ...

    # Make sure we have the device encryption key.
    start vold
    installkey /data

    # Start bootcharting as soon as possible after the data partition is
    # mounted to collect more data.
    mkdir /data/bootchart 0755 shell shell
    bootchart_init

    # Avoid predictable entropy pool. Carry over entropy from previous boot.
    copy /data/system/entropy.dat /dev/urandom
    ...
    init_user0
```

installkey
----------------------------------------

#### 1.do_installkey

path: system/core/init/builtins.cpp
```
static int do_installkey(const std::vector<std::string>& args) {
    if (!is_file_crypto()) {
        return 0;
    }
    return e4crypt_create_device_key(args[1].c_str(),
                                     do_installkeys_ensure_dir_exists);
}
```

#### 2.e4crypt_create_device_key

path: system/extras/ext4_utils/ext4_crypt_init_extensions.cpp
```
int e4crypt_create_device_key(const char* dir,
                              int ensure_dir_exists(const char*))
{
    init_logging();

    // Make sure folder exists. Use make_dir to set selinux permissions.
    std::string unencrypted_dir = std::string(dir) + e4crypt_unencrypted_folder;
    if (ensure_dir_exists(unencrypted_dir.c_str())) {
        KLOG_ERROR(TAG, "Failed to create %s (%s)\n",
                   unencrypted_dir.c_str(),
                   strerror(errno));
        return -1;
    }

    const char* argv[] = { "/system/bin/vdc", "--wait", "cryptfs", "enablefilecrypto" };
    int rc = android_fork_execvp(4, (char**) argv, NULL, false, true);
    LOG(INFO) << "enablefilecrypto result: " << rc;
    return rc;
}
```

#### 3.enablefilecrypto

path: system/vold/cryptfs.c
```
int cryptfs_enable_file()
{
    return e4crypt_initialize_global_de();
}
```

#### 4.e4crypt_initialize_global_de

path: system/vold/Ext4Crypt.cpp
```
bool e4crypt_initialize_global_de() {
    LOG(INFO) << "e4crypt_initialize_global_de";

    if (s_global_de_initialized) {
        LOG(INFO) << "Already initialized";
        return true;
    }

    std::string device_key;
    if (path_exists(device_key_path)) {
        if (!android::vold::retrieveKey(device_key_path,
                kEmptyAuthentication, &device_key)) return false;
    } else {
        LOG(INFO) << "Creating new key";
        if (!random_key(&device_key)) return false;
        if (!store_key(device_key_path, device_key_temp,
                kEmptyAuthentication, device_key)) return false;
    }

    std::string device_key_ref;
    if (!install_key(device_key, &device_key_ref)) {
        LOG(ERROR) << "Failed to install device key";
        return false;
    }

    std::string ref_filename = std::string("/data") + e4crypt_key_ref;
    if (!android::base::WriteStringToFile(device_key_ref, ref_filename)) {
        PLOG(ERROR) << "Cannot save key reference";
        return false;
    }

    s_global_de_initialized = true;
    return true;
}
```

#### 5.install_key

path: system/vold/Ext4Crypt.cpp
```
// Install password into global keyring
// Return raw key reference for use in policy
static bool install_key(const std::string& key, std::string* raw_ref) {
    ext4_encryption_key ext4_key;
    if (!fill_key(key, &ext4_key)) return false;
    *raw_ref = generate_key_ref(ext4_key.raw, ext4_key.size);
    auto ref = keyname(*raw_ref);
    key_serial_t device_keyring;
    if (!e4crypt_keyring(&device_keyring)) return false;
    key_serial_t key_id =
        add_key("logon", ref.c_str(), (void*)&ext4_key, sizeof(ext4_key), device_keyring);
    if (key_id == -1) {
        PLOG(ERROR) << "Failed to insert key into keyring " << device_keyring;
        return false;
    }
    LOG(DEBUG) << "Added key " << key_id << " (" << ref << ") to keyring " << device_keyring
               << " in process " << getpid();
    return true;
}
```

mkdir
----------------------------------------

#### 1.do_mkdir

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

#### 2.e4crypt_is_native

path: path: system/extras/ext4_utils/ext4_crypt.cpp
```
bool e4crypt_is_native() {
    char value[PROPERTY_VALUE_MAX];
    property_get("ro.crypto.type", value, "none");
    return !strcmp(value, "file");
}
```

#### 3.e4crypt_set_directory_policy

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

#### 4.e4crypt_policy_ensure

path: system/extras/ext4_utils/ext4_crypt.cpp
```
int e4crypt_policy_ensure(const char *directory, const char *policy, size_t policy_length) {
    bool is_empty;
    if (!is_dir_empty(directory, &is_empty)) return -1;
    if (is_empty) {
        if (!e4crypt_policy_set(directory, policy, policy_length)) return -1;
    } else {
        if (!e4crypt_policy_check(directory, policy, policy_length)) return -1;
    }
    return 0;
}
```

#### 5.e4crypt_policy_set

path: system/extras/ext4_utils/ext4_crypt.cpp
```
static bool e4crypt_policy_set(const char *directory, const char *policy, size_t policy_length) {
    if (policy_length != EXT4_KEY_DESCRIPTOR_SIZE) {
        LOG(ERROR) << "Policy wrong length: " << policy_length;
        return false;
    }
    int fd = open(directory, O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (fd == -1) {
        PLOG(ERROR) << "Failed to open directory " << directory;
        return false;
    }

    ext4_encryption_policy eep;
    eep.version = 0;
    eep.contents_encryption_mode = EXT4_ENCRYPTION_MODE_PRIVATE;
    eep.filenames_encryption_mode = EXT4_ENCRYPTION_MODE_AES_256_CTS;
    eep.flags = 0;
    memcpy(eep.master_key_descriptor, policy, EXT4_KEY_DESCRIPTOR_SIZE);
    if (ioctl(fd, EXT4_IOC_SET_ENCRYPTION_POLICY, &eep)) {
        PLOG(ERROR) << "Failed to set encryption policy for " << directory;
        close(fd);
        return false;
    }
    close(fd);

    char policy_hex[EXT4_KEY_DESCRIPTOR_SIZE_HEX];
    policy_to_hex(policy, policy_hex);
    LOG(INFO) << "Policy for " << directory << " set to " << policy_hex;
    return true;
}
```

init_user0
----------------------------------------

path: system/core/init/builtins.cpp
```
static int do_init_user0(const std::vector<std::string>& args) {
    return e4crypt_do_init_user0();
}
```

#### 1.e4crypt_do_init_user0

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

#### 2.e4crypt_init_user0

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
