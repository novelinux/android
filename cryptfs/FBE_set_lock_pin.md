# FBE Set Lock Pin

* Encrypt

```
04-22 12:29:18.777   661   668 I vold    : Policy for /data/system_de/0 set to c7547f3d6ff91ec5
04-22 12:29:18.777   661   668 I vold    : Policy for /data/misc_de/0 set to c7547f3d6ff91ec5
04-22 12:29:18.777   661   668 I vold    : Policy for /data/user_de/0 set to c7547f3d6ff91ec5
...
04-22 12:30:37.402   661   668 I vold    : Policy for /data/system_ce/0 set to 2306d22d0a799add
04-22 12:30:37.402   661   668 I vold    : Policy for /data/misc_ce/0 set to 2306d22d0a799add
04-22 12:30:37.403   661   668 I vold    : Policy for /data/media/0 set to 2306d22d0a799add
04-22 12:30:37.403   661   668 I vold    : Policy for /data/data set to 2306d22d0a799add
```

* Uncrypt

```
04-22 13:19:12.216   651   657 E vold    : The exists entry: accounts_de.db: Success
04-22 13:19:12.216   651   657 I vold    : Found policy c7547f3d6ff91ec5 at /data/system_de/0 which matches expected value
04-22 13:19:12.217   651   657 I vold    : Policy for /data/misc_de/0 set to c7547f3d6ff91ec5
04-22 13:19:12.217   651   657 E vold    : The exists entry: com.miui.screenrecorder: Success
04-22 13:19:12.217   651   657 I vold    : Found policy c7547f3d6ff91ec5 at /data/user_de/0 which matches expected value
...
04-22 13:20:25.830   651   657 I vold    : Found policy 2306d22d0a799add at /data/system_ce/0 which matches expected value
04-22 13:20:25.830   651   657 I vold    : Policy for /data/misc_ce/0 set to 2306d22d0a799add
04-22 13:20:25.831   651   657 E vold    : The exists entry: MIUI: Success
04-22 13:20:25.831   651   657 I vold    : Found policy 2306d22d0a799add at /data/media/0 which matches expected value
04-22 13:20:25.831   651   657 E vold    : The exists entry: com.miui.screenrecorder: Success
04-22 13:20:25.831   651   657 I vold    : Found policy 2306d22d0a799add at /data/data which matches expected value
```

## Lock

* LOGS

```
04-22 13:12:16.860  1823  1864 D CryptdConnector: SND -> {4 cryptfs add_user_key_auth 0 0 [scrubbed] [scrubbed]}
04-22 13:12:16.861   661   668 D vold    : e4crypt_add_user_key_auth 0 serial=0 token_present=1
04-22 13:12:16.862   661   668 D vold    : Skipping non-key .
04-22 13:12:16.862   661   668 D vold    : Skipping non-key ..
...
04-22 13:12:17.420   661   668 E keymaster1_device: Keymaster Initialized
04-22 13:12:17.420   661   668 E keymaster1_device: TA API Major Verion: 2
04-22 13:12:17.420   661   668 E keymaster1_device: TA API Minor Verion: 0
04-22 13:12:17.420   661   668 E keymaster1_device: TA Major Verion: 2
04-22 13:12:17.420   661   668 E keymaster1_device: TA Minor Verion: 25
04-22 13:12:17.420   661   668 E keymaster1_device: set_version_req->flags: 1
04-22 13:12:17.422   661   668 D vold    : Auth token required for key
04-22 13:12:17.425   632   637 D DrmLibTime: got the req here! ret=0
...
04-22 13:12:17.441   661   668 D vold    : Supplying auth token to Keymaster
04-22 13:12:17.452   661   668 E keymaster1_device: HwKmClose
04-22 13:12:17.452   661   668 D vold    : Created key /data/misc/vold/user_keys/ce/0/cx0000000000
04-22 13:12:17.453  1823  2471 D CryptdConnector: RCV <- {200 4 Command succeeded}
04-22 13:12:17.454  1823  1864 E CryptdConnector: NDC Command {4 cryptfs add_user_key_auth 0 0 [scrubbed] [scrubbed]} took too long (594ms)
04-22 13:12:17.461  1823  1864 D SpaceEncryptionService: in ecryptfsUpdate(0, authData)
04-22 13:12:17.463  1823  1864 D CryptdConnector: SND -> {5 cryptfs fixate_newest_user_key_auth 0}
04-22 13:12:17.466   661   668 D vold    : e4crypt_fixate_newest_user_key_auth 0
04-22 13:12:17.466   661   668 D vold    : Skipping non-key .
04-22 13:12:17.466   661   668 D vold    : Skipping non-key ..
04-22 13:12:17.473   661   668 E keymaster1_device: Keymaster Initialized
04-22 13:12:17.473   661   668 E keymaster1_device: TA API Major Verion: 2
04-22 13:12:17.473   661   668 E keymaster1_device: TA API Minor Verion: 0
04-22 13:12:17.473   661   668 E keymaster1_device: TA Major Verion: 2
04-22 13:12:17.473   661   668 E keymaster1_device: TA Minor Verion: 25
04-22 13:12:17.473   661   668 E keymaster1_device: set_version_req->flags: 1
04-22 13:12:17.481   661   668 E keymaster1_device: HwKmClose
04-22 13:12:17.481   661   668 V vold    : /system/bin/secdiscard
04-22 13:12:17.481   661   668 V vold    :     --
04-22 13:12:17.481   661   668 V vold    :     /data/misc/vold/user_keys/ce/0/current/encrypted_key
04-22 13:12:17.481   661   668 V vold    :     /data/misc/vold/user_keys/ce/0/current/keymaster_key_blob
04-22 13:12:17.481   661   668 V vold    :     /data/misc/vold/user_keys/ce/0/current/secdiscardable
04-22 13:12:17.526  6927  6927 D secdiscard: Securely discarding '/data/misc/vold/user_keys/ce/0/current/encrypted_key' unlink=1
04-22 13:12:17.527  6927  6927 D secdiscard: For path /data/misc/vold/user_keys/ce/0/current/encrypted_key block device is /dev/block/bootdevice/by-name/userdata
04-22 13:12:17.527  6927  6927 E secdiscard: Unable to BLKSECDISCARD /data/misc/vold/user_keys/ce/0/current/encrypted_key: Operation not supported on transport endpoint
04-22 13:12:17.528  6927  6927 D secdiscard: Used zero overwrite
04-22 13:12:17.528  6927  6927 D secdiscard: Discarded: /data/misc/vold/user_keys/ce/0/current/encrypted_key
04-22 13:12:17.528  6927  6927 D secdiscard: Securely discarding '/data/misc/vold/user_keys/ce/0/current/keymaster_key_blob' unlink=1
04-22 13:12:17.529  6927  6927 D secdiscard: For path /data/misc/vold/user_keys/ce/0/current/keymaster_key_blob block device is /dev/block/bootdevice/by-name/userdata
04-22 13:12:17.529  6927  6927 E secdiscard: Unable to BLKSECDISCARD /data/misc/vold/user_keys/ce/0/current/keymaster_key_blob: Operation not supported on transport endpoint
04-22 13:12:17.529  6927  6927 D secdiscard: Used zero overwrite
04-22 13:12:17.529  6927  6927 D secdiscard: Discarded: /data/misc/vold/user_keys/ce/0/current/keymaster_key_blob
04-22 13:12:17.529  6927  6927 D secdiscard: Securely discarding '/data/misc/vold/user_keys/ce/0/current/secdiscardable' unlink=1
04-22 13:12:17.530  6927  6927 D secdiscard: For path /data/misc/vold/user_keys/ce/0/current/secdiscardable block device is /dev/block/bootdevice/by-name/userdata
04-22 13:12:17.530  6927  6927 E secdiscard: Unable to BLKSECDISCARD /data/misc/vold/user_keys/ce/0/current/secdiscardable: Operation not supported on transport endpoint
04-22 13:12:17.530  6927  6927 D secdiscard: Used zero overwrite
04-22 13:12:17.530  6927  6927 D secdiscard: Discarded: /data/misc/vold/user_keys/ce/0/current/secdiscardable
04-22 13:12:17.533   661   668 V vold    : /system/bin/rm
04-22 13:12:17.534   661   668 V vold    :     -rf
04-22 13:12:17.534   661   668 V vold    :     /data/misc/vold/user_keys/ce/0/current
04-22 13:12:17.557   661   668 D vold    : Renaming /data/misc/vold/user_keys/ce/0/cx0000000000 to /data/misc/vold/user_keys/ce/0/current
04-22 13:12:17.557  1823  2471 D CryptdConnector: RCV <- {200 5 Command succeeded}
04-22 13:12:17.585  3928  3928 D NfcService: Enforcing a policy change on user: UserHandle{0}, isActiveForUser = true
04-22 13:12:17.609   849   849 I         : calling unlock when already unlocked, ignoring.
04-22 13:12:17.609  1823  4055 I LockSettingsService: Unlocking user 0 with token length 69
04-22 13:12:17.609  1823  4055 D LockSettingsService: unlockUser finished
04-22 13:12:17.610  1823  4055 W ActivityManager: Expected user 0 in state RUNNING_LOCKED but was in state RUNNING_UNLOCKED
04-22 13:12:17.615  1823  4057 D CryptdConnector: SND -> {6 cryptfs changepw default [scrubbed] [scrubbed]}
04-22 13:12:17.616   661   668 D VoldCryptCmdListener: cryptfs changepw default {}
04-22 13:12:17.616   661   668 E Cryptfs : cryptfs_changepw not valid for file encryption
04-22 13:12:17.616  1823  2471 D CryptdConnector: RCV <- {200 6 -1}
```

## Unlock

```
04-22 13:20:25.248   651   657 D vold    : e4crypt_unlock_user_key 0 serial=0 token_present=1
...
04-22 13:20:25.251   651   657 D vold    : Skipping non-key .
04-22 13:20:25.251   651   657 D vold    : Skipping non-key ..
04-22 13:20:25.251   651   657 D vold    : Trying user CE key /data/misc/vold/user_keys/ce/0/current
...
04-22 13:20:25.791   651   657 E keymaster1_device: Keymaster Initialized
04-22 13:20:25.791   651   657 E keymaster1_device: TA API Major Verion: 2
04-22 13:20:25.791   651   657 E keymaster1_device: TA API Minor Verion: 0
04-22 13:20:25.791   651   657 E keymaster1_device: TA Major Verion: 2
04-22 13:20:25.791   651   657 E keymaster1_device: TA Minor Verion: 25
04-22 13:20:25.791   651   657 E keymaster1_device: set_version_req->flags: 1
04-22 13:20:25.793   651   657 D vold    : Supplying auth token to Keymaster
04-22 13:20:25.805   651   657 E keymaster1_device: HwKmClose
04-22 13:20:25.805   651   657 D vold    : Successfully retrieved key
04-22 13:20:25.805   651   657 D vold    : Added key 389256964 (ext4:2306d22d0a799add) to keyring 277376860 in process 651
04-22 13:20:25.805   651   657 D vold    : Installed ce key for user 0
04-22 13:20:25.806  1616  1941 D CryptdConnector: RCV <- {200 2 Command succeeded}
04-22 13:20:25.808  1616  2189 E CryptdConnector: NDC Command {2 cryptfs unlock_user_key 0 0 [scrubbed] [scrubbed]} took too long (560ms)
...
04-22 13:20:25.811  1616  2189 D LockSettingsService: unlockUser started
04-22 13:20:25.811  1616  2189 D LockSettingsService: unlockUser progress 0
04-22 13:20:25.812  1616  2189 D LockSettingsService: unlockUser progress 5
...
04-22 13:20:25.816   651   657 D vold    : e4crypt_prepare_user_storage for volume null, user 0, serial 0, flags 2
04-22 13:20:25.816   651   657 D vold    : Preparing: /data/system_ce/0
04-22 13:20:25.816   651   657 D vold    : Preparing: /data/misc_ce/0
04-22 13:20:25.828  1616  1816 I Choreographer: Skipped 34 frames!  The application may be doing too much work on its main thread.
04-22 13:20:25.828   651   657 D vold    : Preparing: /data/media/0
04-22 13:20:25.828   651   657 D vold    : Preparing: /data/data
04-22 13:20:25.830   651   657 E vold    : The exists entry: recent_tasks: Success
04-22 13:20:25.830   651   657 I vold    : Found policy 2306d22d0a799add at /data/system_ce/0 which matches expected value
04-22 13:20:25.830   651   657 I vold    : Policy for /data/misc_ce/0 set to 2306d22d0a799add
04-22 13:20:25.831   651   657 E vold    : The exists entry: MIUI: Success
04-22 13:20:25.831   651   657 I vold    : Found policy 2306d22d0a799add at /data/media/0 which matches expected value
04-22 13:20:25.831   651   657 E vold    : The exists entry: com.miui.screenrecorder: Success
04-22 13:20:25.831   651   657 I vold    : Found policy 2306d22d0a799add at /data/data which matches expected value
04-22 13:20:25.831   651   657 V vold    : Starting restorecon of /data/system_ce/0
04-22 13:20:25.834   651   657 V vold    : Finished restorecon of /data/system_ce/0
04-22 13:20:25.834   651   657 V vold    : Starting restorecon of /data/misc_ce/0
04-22 13:20:25.839   651   657 V vold    : Finished restorecon of /data/misc_ce/0
04-22 13:20:25.839  1616  1941 D CryptdConnector: RCV <- {200 3 Command succeeded}
04-22 13:20:25.841  1616  2189 V UserManagerService: Found /data/user/0 with serial number 0
04-22 13:20:25.841  1616  2189 V UserManagerService: Found /data/system_ce/0 with serial number 0
04-22 13:20:25.842  1616  2189 V PackageManager: reconcileAppsData for null u0 0x2
```