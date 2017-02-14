FBE Keys Generation
========================================

Generate unencrypted Key
----------------------------------------

* device_key_path - "/data/unencrypted/key"

```
sagit:/ # ls -l /data/unencrypted/key/
total 64
-rw------- 1 root root    92 1970-02-03 08:46 encrypted_key
-rw------- 1 root root   427 1970-02-03 08:46 keymaster_key_blob
-rw------- 1 root root 16384 1970-02-03 08:46 secdiscardable
-rw------- 1 root root    10 1970-02-03 08:46 stretching
-rw------- 1 root root     1 1970-02-03 08:46 version
```

* device_key_ref - "/data/unencrypted/ref"

```
sagit:/ # ls -l /data/unencrypted/ref
-rw------- 1 root root 8 1970-02-03 08:46 /data/unencrypted/ref
```

* LOGS

```
01-15 06:48:24.514   592   598 E keymaster1_device: HwKmClose
01-15 06:48:24.514   592   598 D vold    : Created key /data/unencrypted/key
01-15 06:48:24.514   592   598 D vold    : Added key 123488388 (ext4:0e30728e813bbb1b) to keyring 1063057322 in process 592
01-15 06:48:24.515     1     1 I vdc     : 200 593 1
```

### do_installkey

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

### e4crypt_create_device_key

path: system/extras/ext4_utils/ext4_crypt.h
```
static const char* e4crypt_unencrypted_folder = "/unencrypted";
static const char* e4crypt_key_ref = "/unencrypted/ref";
```

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

### cryptfs_enable_file

path: system/vold/cryptfs.c
```
int cryptfs_enable_file()
{
    return e4crypt_initialize_global_de();
}
```

### e4crypt_initialize_global_de

path: system/vold/Ext4Crypt.cpp
```
const std::string device_key_dir = std::string() + DATA_MNT_POINT + e4crypt_unencrypted_folder;
const std::string device_key_path = device_key_dir + "/key";
const std::string device_key_temp = device_key_dir + "/temp";
...
bool e4crypt_initialize_global_de() {
    LOG(INFO) << "e4crypt_initialize_global_de";

    if (s_global_de_initialized) {
        LOG(INFO) << "Already initialized";
        return true;
    }

    std::string mode_filename = std::string("/data") + e4crypt_key_mode;
    std::string mode = cryptfs_get_file_encryption_mode();
    if (!android::base::WriteStringToFile(mode, mode_filename)) {
        PLOG(ERROR) << "Cannot save type";
        return false;
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

Generate DE vs CE directory Keys
----------------------------------------

* /data/misc/vold/user_keys:

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

* LOGS

```
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
```

### do_init_user0

path: system/core/init/builtins.cpp
```
static int do_init_user0(const std::vector<std::string>& args) {
    return e4crypt_do_init_user0();
}
```

### e4crypt_do_init_user0

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

### e4crypt_init_user0

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

### create_and_install_user_keys

```
static bool create_and_install_user_keys(userid_t user_id, bool create_ephemeral) {
    std::string de_key, ce_key;
    if (!random_key(&de_key)) return false;
    if (!random_key(&ce_key)) return false;
    if (create_ephemeral) {
        // If the key should be created as ephemeral, don't store it.
        s_ephemeral_users.insert(user_id);
    } else {
        auto const directory_path = get_ce_key_directory_path(user_id);
        if (!prepare_dir(directory_path, 0700, AID_ROOT, AID_ROOT)) return false;
        auto const paths = get_ce_key_paths(directory_path);
        std::string ce_key_path;
        if (!get_ce_key_new_path(directory_path, paths, &ce_key_path)) return false;
        if (!store_key(ce_key_path, user_key_temp,
                kEmptyAuthentication, ce_key)) return false;
        fixate_user_ce_key(directory_path, ce_key_path, paths);
        // Write DE key second; once this is written, all is good.
        if (!store_key(get_de_key_path(user_id), user_key_temp,
                kEmptyAuthentication, de_key)) return false;
    }
    std::string de_raw_ref;
    if (!install_key(de_key, &de_raw_ref)) return false;
    s_de_key_raw_refs[user_id] = de_raw_ref;
    std::string ce_raw_ref;
    if (!install_key(ce_key, &ce_raw_ref)) return false;
    s_ce_keys[user_id] = ce_key;
    s_ce_key_raw_refs[user_id] = ce_raw_ref;
    LOG(DEBUG) << "Created keys for user " << user_id;
    return true;
}
```

### load_all_de_keys

```
static bool load_all_de_keys() {
    auto de_dir = user_key_dir + "/de";
    auto dirp = std::unique_ptr<DIR, int (*)(DIR*)>(opendir(de_dir.c_str()), closedir);
    if (!dirp) {
        PLOG(ERROR) << "Unable to read de key directory";
        return false;
    }
    for (;;) {
        errno = 0;
        auto entry = readdir(dirp.get());
        if (!entry) {
            if (errno) {
                PLOG(ERROR) << "Unable to read de key directory";
                return false;
            }
            break;
        }
        if (entry->d_type != DT_DIR || !is_numeric(entry->d_name)) {
            LOG(DEBUG) << "Skipping non-de-key " << entry->d_name;
            continue;
        }
        userid_t user_id = atoi(entry->d_name);
        if (s_de_key_raw_refs.count(user_id) == 0) {
            auto key_path = de_dir + "/" + entry->d_name;
            std::string key;
            if (!android::vold::retrieveKey(key_path, kEmptyAuthentication, &key)) return false;
            std::string raw_ref;
            if (!install_key(key, &raw_ref)) return false;
            s_de_key_raw_refs[user_id] = raw_ref;
            LOG(DEBUG) << "Installed de key for user " << user_id;
        }
    }
    // ext4enc:TODO: go through all DE directories, ensure that all user dirs have the
    // correct policy set on them, and that no rogue ones exist.
    return true;
}
```

install_key
----------------------------------------

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

### generate_key_ref

path: system/vold/Ext4Crypt.cpp
```
// Get raw keyref - used to make keyname and to pass to ioctl
static std::string generate_key_ref(const char* key, int length) {
    SHA512_CTX c;

    SHA512_Init(&c);
    SHA512_Update(&c, key, length);
    unsigned char key_ref1[SHA512_DIGEST_LENGTH];
    SHA512_Final(key_ref1, &c);

    SHA512_Init(&c);
    SHA512_Update(&c, key_ref1, SHA512_DIGEST_LENGTH);
    unsigned char key_ref2[SHA512_DIGEST_LENGTH];
    SHA512_Final(key_ref2, &c);

    static_assert(EXT4_KEY_DESCRIPTOR_SIZE <= SHA512_DIGEST_LENGTH,
                  "Hash too short for descriptor");
    return std::string((char*)key_ref2, EXT4_KEY_DESCRIPTOR_SIZE);
}
```

store_key
----------------------------------------

path: system/vold/Ext4Crypt.cpp
```
// NB this assumes that there is only one thread listening for crypt commands, because
// it creates keys in a fixed location.
static bool store_key(const std::string& key_path, const std::string& tmp_path,
                      const android::vold::KeyAuthentication& auth, const std::string& key) {
    if (path_exists(key_path)) {
        LOG(ERROR) << "Already exists, cannot create key at: " << key_path;
        return false;
    }
    if (path_exists(tmp_path)) {
        android::vold::destroyKey(tmp_path);  // May be partially created so ignore errors
    }
    if (!android::vold::storeKey(tmp_path, auth, key)) return false;
    if (rename(tmp_path.c_str(), key_path.c_str()) != 0) {
        PLOG(ERROR) << "Unable to move new key to location: " << key_path;
        return false;
    }
    LOG(DEBUG) << "Created key " << key_path;
    return true;
}
```

android::vold::retrieveKey
----------------------------------------

path: system/vold/KeyStorage.cpp
```
static const char* kFn_encrypted_key = "encrypted_key";
static const char* kFn_keymaster_key_blob = "keymaster_key_blob";
static const char* kFn_salt = "salt";
static const char* kFn_secdiscardable = "secdiscardable";
static const char* kFn_stretching = "stretching";
static const char* kFn_version = "version";

...

bool retrieveKey(const std::string& dir, const KeyAuthentication& auth, std::string* key) {
    std::string version;
    if (!readFileToString(dir + "/" + kFn_version, &version)) return false;
    if (version != kCurrentVersion) {
        LOG(ERROR) << "Version mismatch, expected " << kCurrentVersion << " got " << version;
        return false;
    }
    std::string secdiscardable;
    if (!readFileToString(dir + "/" + kFn_secdiscardable, &secdiscardable)) return false;
    std::string stretching;
    if (!readFileToString(dir + "/" + kFn_stretching, &stretching)) return false;
    std::string salt;
    if (stretchingNeedsSalt(stretching)) {
        if (!readFileToString(dir + "/" + kFn_salt, &salt)) return false;
    }
    std::string appId;
    if (!generateAppId(auth, stretching, salt, secdiscardable, &appId)) return false;
    std::string kmKey;
    if (!readFileToString(dir + "/" + kFn_keymaster_key_blob, &kmKey)) return false;
    std::string encryptedMessage;
    if (!readFileToString(dir + "/" + kFn_encrypted_key, &encryptedMessage)) return false;
    Keymaster keymaster;
    if (!keymaster) return false;
    return decryptWithKeymasterKey(keymaster, kmKey, auth, appId, encryptedMessage, key);
}
```