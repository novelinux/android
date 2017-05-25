# Emulator Sdcard Mount

## MountService

```
public MountService(Context context) {
    sSelf = this;

    mContext = context;
    //FgThread线程名为“"android.fg"，创建IMountServiceListener回调方法
    mCallbacks = new Callbacks(FgThread.get().getLooper());
    //获取PKMS的Client端对象
    mPms = (PackageManagerService) ServiceManager.getService("package");
    //创建“MountService”线程
    HandlerThread hthread = new HandlerThread(TAG);
    hthread.start();

    mHandler = new MountServiceHandler(hthread.getLooper());
    //IoThread线程名为"android.io"，创建OBB操作的handler
    mObbActionHandler = new ObbActionHandler(IoThread.get().getLooper());

    File dataDir = Environment.getDataDirectory();
    File systemDir = new File(dataDir, "system");
    mLastMaintenanceFile = new File(systemDir, LAST_FSTRIM_FILE);
    //判断/data/system/last-fstrim文件，不存在则创建，存在则更新最后修改时间
    if (!mLastMaintenanceFile.exists()) {
        (new FileOutputStream(mLastMaintenanceFile)).close();
        ...
    } else {
        mLastMaintenance = mLastMaintenanceFile.lastModified();
    }
    ...
    //将MountServiceInternalImpl登记到sLocalServiceObjects
    LocalServices.addService(MountServiceInternal.class, mMountServiceInternal);
    //创建用于VoldConnector的NDC对象
    mConnector = new NativeDaemonConnector(this, "vold", MAX_CONTAINERS * 2, VOLD_TAG, 25,
            null);
    mConnector.setDebug(true);
    //创建线程名为"VoldConnector"的线程，用于跟vold通信
    Thread thread = new Thread(mConnector, VOLD_TAG);
    thread.start();

    //创建用于CryptdConnector工作的NDC对象
    mCryptConnector = new NativeDaemonConnector(this, "cryptd",
            MAX_CONTAINERS * 2, CRYPTD_TAG, 25, null);
    mCryptConnector.setDebug(true);
    //创建线程名为"CryptdConnector"的线程，用于加密
    Thread crypt_thread = new Thread(mCryptConnector, CRYPTD_TAG);
    crypt_thread.start();

    //注册监听用户添加、删除的广播
    final IntentFilter userFilter = new IntentFilter();
    userFilter.addAction(Intent.ACTION_USER_ADDED);
    userFilter.addAction(Intent.ACTION_USER_REMOVED);
    mContext.registerReceiver(mUserReceiver, userFilter, null, mHandler);

    //内部私有volume的路径为/data，该volume通过dumpsys mount是不会显示的
    addInternalVolume();

    //默认为false
    if (WATCHDOG_ENABLE) {
        Watchdog.getInstance().addMonitor(this);
    }
}
```

## handleDaemonConnected

* LOGS

```
01-01 00:28:37.591  1262  1645 D MountService: Thinking about init, mSystemReady=false, mDaemonConnected=true
01-01 00:28:37.591  1262  1645 D MountService: Thinking about reset, mSystemReady=false, mDaemonConnected=true
01-01 00:28:37.606  1262  1645 D MountService: Thinking about init, mSystemReady=false, mDaemonConnected=true
01-01 00:28:37.606  1262  1645 D MountService: Thinking about reset, mSystemReady=false, mDaemonConnected=true
```

* function

```
    private void handleDaemonConnected() {
        initIfReadyAndConnected();
        resetIfReadyAndConnected();

        /*
         * Now that we've done our initialization, release
         * the hounds!
         */
        mConnectedSignal.countDown();
        if (mConnectedSignal.getCount() != 0) {
            // More daemons need to connect
            return;
        }

        // On an encrypted device we can't see system properties yet, so pull
        // the system locale out of the mount service.
        if ("".equals(SystemProperties.get("vold.encrypt_progress"))) {
            copyLocaleFromMountService();
        }
    }
```

## copyLocaleFromMountService

* LOGS

```
01-01 00:28:37.618  1262  1645 D MountService: Got locale en-US from mount service
01-01 00:28:37.631  1262  1645 D MountService: Setting system properties to en-US from mount service
```

* function

```
    private void copyLocaleFromMountService() {
        String systemLocale;
        try {
            systemLocale = getField(StorageManager.SYSTEM_LOCALE_KEY);
        } catch (RemoteException e) {
            return;
        }
        if (TextUtils.isEmpty(systemLocale)) {
            return;
        }

        Slog.d(TAG, "Got locale " + systemLocale + " from mount service");
        Locale locale = Locale.forLanguageTag(systemLocale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        try {
            ActivityManagerNative.getDefault().updatePersistentConfiguration(config);
        } catch (RemoteException e) {
            Slog.e(TAG, "Error setting system locale from mount service", e);
        }

        // Temporary workaround for http://b/17945169.
        Slog.d(TAG, "Setting system properties to " + systemLocale + " from mount service");
        SystemProperties.set("persist.sys.locale", locale.toLanguageTag());
    }
```

## getSecureContainerList

* LOGS

```
01-01 00:28:37.643  1262  1645 W MountService: No primary storage mounted!
01-01 00:28:37.644  1262  1645 D VoldConnector: SND -> {1 asec list}
01-01 00:28:37.648  1262  1646 D VoldConnector: RCV <- {200 1 asec operation succeeded}
01-01 00:28:37.649  1262  1645 I chatty  : uid=1000(system) MountService expire 1 line
```

* function

```
    public String[] getSecureContainerList() {
        enforcePermission(android.Manifest.permission.ASEC_ACCESS);
        waitForReady();
        warnOnNotMounted();

        try {
            return NativeDaemonEvent.filterMessageList(
                    mConnector.executeForList("asec", "list"), VoldResponseCode.AsecListResult);
        } catch (NativeDaemonConnectorException e) {
            return new String[0];
        }
    }
```

## getVolumeList

* LOGS

```
01-01 00:28:37.843  1262  1645 W MountService: No primary storage defined yet; hacking together a stub
01-01 00:28:37.848  1262  1645 W MountService: No primary storage defined yet; hacking together a stub
01-01 00:28:37.856  1262  1645 W MountService: No primary storage mounted!
01-01 00:28:37.856  1262  1645 D VoldConnector: SND -> {2 asec list}
01-01 00:28:37.859  1262  1646 D VoldConnector: RCV <- {200 2 asec operation succeeded}
```

```
    @Override
    public StorageVolume[] getVolumeList(int uid, String packageName, int flags) {
        final int userId = UserHandle.getUserId(uid);

        final boolean forWrite = (flags & StorageManager.FLAG_FOR_WRITE) != 0;
        final boolean realState = (flags & StorageManager.FLAG_REAL_STATE) != 0;
        final boolean includeInvisible = (flags & StorageManager.FLAG_INCLUDE_INVISIBLE) != 0;

        final boolean userKeyUnlocked;
        final boolean storagePermission;
        final long token = Binder.clearCallingIdentity();
        try {
            userKeyUnlocked = isUserKeyUnlocked(userId);
            storagePermission = mMountServiceInternal.hasExternalStorage(uid, packageName);
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        boolean foundPrimary = false;

        final ArrayList<StorageVolume> res = new ArrayList<>();
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                switch (vol.getType()) {
                    case VolumeInfo.TYPE_PUBLIC:
                    case VolumeInfo.TYPE_EMULATED:
                        break;
                    default:
                        continue;
                }

                boolean match = false;
                if (forWrite) {
                    match = vol.isVisibleForWrite(userId);
                } else {
                    match = vol.isVisibleForRead(userId)
                            || (includeInvisible && vol.getPath() != null);
                }
                if (!match) continue;

                boolean reportUnmounted = false;
                if ((vol.getType() == VolumeInfo.TYPE_EMULATED) && !userKeyUnlocked) {
                    reportUnmounted = true;
                } else if (!storagePermission && !realState) {
                    reportUnmounted = true;
                }

                final StorageVolume userVol = vol.buildStorageVolume(mContext, userId,
                        reportUnmounted);
                if (vol.isPrimary()) {
                    res.add(0, userVol);
                    foundPrimary = true;
                } else {
                    res.add(userVol);
                }
            }
        }

        if (!foundPrimary) {
            Log.w(TAG, "No primary storage defined yet; hacking together a stub");

            final boolean primaryPhysical = SystemProperties.getBoolean(
                    StorageManager.PROP_PRIMARY_PHYSICAL, false);

            final String id = "stub_primary";
            final File path = Environment.getLegacyExternalStorageDirectory();
            final String description = mContext.getString(android.R.string.unknownName);
            final boolean primary = true;
            final boolean removable = primaryPhysical;
            final boolean emulated = !primaryPhysical;
            final long mtpReserveSize = 0L;
            final boolean allowMassStorage = false;
            final long maxFileSize = 0L;
            final UserHandle owner = new UserHandle(userId);
            final String uuid = null;
            final String state = Environment.MEDIA_REMOVED;

            res.add(0, new StorageVolume(id, StorageVolume.STORAGE_ID_INVALID, path,
                    description, primary, removable, emulated, mtpReserveSize,
                    allowMassStorage, maxFileSize, owner, uuid, state));
        }

        return res.toArray(new StorageVolume[res.size()]);
    }
```

## handleSystemReady

* function

```
    private void handleSystemReady() {
        initIfReadyAndConnected();
        resetIfReadyAndConnected();

        // Start scheduling nominally-daily fstrim operations
        MountServiceIdler.scheduleIdlePass(mContext);
    }
```

* LOGS

```
01-01 00:28:40.163  1262  1645 D MountService: Thinking about init, mSystemReady=true, mDaemonConnected=true
01-01 00:28:40.164  1262  1645 D MountService: Setting up emulation state, initlocked=false
01-01 00:28:40.164  1262  1645 D CryptdConnector: SND -> {2 cryptfs unlock_user_key 0 0 ! !}
01-01 00:28:40.165   463   469 D vold    : e4crypt_unlock_user_key 0 serial=0 token_present=0
01-01 00:28:40.165  1262  1647 D CryptdConnector: RCV <- {200 2 Command succeeded}
```

### initIfReadyAndConnected

```
    private void initIfReadyAndConnected() {
        Slog.d(TAG, "Thinking about init, mSystemReady=" + mSystemReady
                + ", mDaemonConnected=" + mDaemonConnected);
        if (mSystemReady && mDaemonConnected
                && !StorageManager.isFileEncryptedNativeOnly()) {
            // When booting a device without native support, make sure that our
            // user directories are locked or unlocked based on the current
            // emulation status.
            final boolean initLocked = StorageManager.isFileEncryptedEmulatedOnly();
            Slog.d(TAG, "Setting up emulation state, initlocked=" + initLocked);
            final List<UserInfo> users = mContext.getSystemService(UserManager.class).getUsers();
            for (UserInfo user : users) {
                try {
                    if (initLocked) {
                        mCryptConnector.execute("cryptfs", "lock_user_key", user.id);
                    } else {
                        mCryptConnector.execute("cryptfs", "unlock_user_key", user.id,
                                user.serialNumber, "!", "!");
                    }
                } catch (NativeDaemonConnectorException e) {
                    Slog.w(TAG, "Failed to init vold", e);
                }
            }
        }
    }
```

* LOGS

```
01-01 00:28:40.169  1262  1645 D MountService: Thinking about reset, mSystemReady=true, mDaemonConnected=true
01-01 00:28:40.169  1262  1645 D VoldConnector: SND -> {3 volume reset}
01-01 00:28:40.170  1262  1646 D VoldConnector: RCV <- {651 emulated 7}
01-01 00:28:40.171  1262  1646 D VoldConnector: RCV <- {659 emulated}
01-01 00:28:40.171  1262  1646 D VoldConnector: RCV <- {650 emulated 2 "" ""}
01-01 00:28:40.172  1262  1646 D VoldConnector: RCV <- {651 emulated 0}
01-01 00:28:40.172  1262  1646 D VoldConnector: RCV <- {200 3 Command succeeded}
01-01 00:28:40.173  1262  1360 V MountService: Found primary storage at VolumeInfo{emulated}:
01-01 00:28:40.173  1262  1360 V MountService:     type=EMULATED diskId=null partGuid=null mountFlags=0 mountUserId=-1
01-01 00:28:40.173  1262  1360 V MountService:     state=UNMOUNTED
01-01 00:28:40.173  1262  1360 V MountService:     fsType=null fsUuid=null fsLabel=null
01-01 00:28:40.173  1262  1360 V MountService:     path=null internalPath=null
01-01 00:28:40.174  1262  1645 D VoldConnector: SND -> {4 volume user_added 0 0}
01-01 00:28:40.175  1262  1646 D VoldConnector: RCV <- {200 4 Command succeeded}

01-01 00:28:40.210  1262  1645 D VoldConnector: SND -> {5 volume mount emulated 3 -1}
01-01 00:28:40.218  1262  1646 D VoldConnector: RCV <- {651 emulated 1}
01-01 00:28:40.218  1262  1646 D VoldConnector: RCV <- {656 emulated /data/media}
01-01 00:28:40.219  1262  1646 D VoldConnector: RCV <- {655 emulated /storage/emulated}
01-01 00:28:40.220   463   468 V vold    : Waiting for FUSE to spin up...
01-01 00:28:40.270   463   468 V vold    : Waiting for FUSE to spin up...
01-01 00:28:40.321  1262  1646 D VoldConnector: RCV <- {651 emulated 2}
01-01 00:28:40.322  1262  1646 D VoldConnector: RCV <- {200 5 Command succeeded}

01-01 00:28:48.965  1262  1327 D MountService: onUnlockUser 0
01-01 00:28:48.965  1262  1327 D VoldConnector: SND -> {6 volume user_started 0}
01-01 00:28:48.984   463   468 D vold    : Linking /storage/emulated/0 to /mnt/user/0/primary
01-01 00:28:48.985  1262  1646 D VoldConnector: RCV <- {200 6 Command succeeded}
```

* codes

```
        public static final int VOLUME_CREATED = 650;
        public static final int VOLUME_STATE_CHANGED = 651;
        ...
        public static final int VOLUME_DESTROYED = 659;
```

### resetIfReadyAndConnected

```
    private void resetIfReadyAndConnected() {
        Slog.d(TAG, "Thinking about reset, mSystemReady=" + mSystemReady
                + ", mDaemonConnected=" + mDaemonConnected);
        if (mSystemReady && mDaemonConnected) {
            final List<UserInfo> users = mContext.getSystemService(UserManager.class).getUsers();
            killMediaProvider(users);

            final int[] systemUnlockedUsers;
            synchronized (mLock) {
                systemUnlockedUsers = mSystemUnlockedUsers;

                mDisks.clear();
                mVolumes.clear();

                addInternalVolumeLocked();
            }

            try {
                mConnector.execute("volume", "reset");

                // Tell vold about all existing and started users
                for (UserInfo user : users) {
                    mConnector.execute("volume", "user_added", user.id, user.serialNumber);
                }
                for (int userId : systemUnlockedUsers) {
                    mConnector.execute("volume", "user_started", userId);
                }
            } catch (NativeDaemonConnectorException e) {
                Slog.w(TAG, "Failed to reset vold", e);
            }
        }
    }
```

### onVolumeCreatedLocked

```
    private void onVolumeCreatedLocked(VolumeInfo vol) {
        if (mPms.isOnlyCoreApps()) {
            Slog.d(TAG, "System booted in core-only mode; ignoring volume " + vol.getId());
            return;
        }

        if (vol.type == VolumeInfo.TYPE_EMULATED) {
            final StorageManager storage = mContext.getSystemService(StorageManager.class);
            final VolumeInfo privateVol = storage.findPrivateForEmulated(vol);

            if (Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, mPrimaryStorageUuid)
                    && VolumeInfo.ID_PRIVATE_INTERNAL.equals(privateVol.id)) {
                Slog.v(TAG, "Found primary storage at " + vol);
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_PRIMARY;
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
                mHandler.obtainMessage(H_VOLUME_MOUNT, vol).sendToTarget();

            } else if (Objects.equals(privateVol.fsUuid, mPrimaryStorageUuid)) {
                Slog.v(TAG, "Found primary storage at " + vol);
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_PRIMARY;
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
                mHandler.obtainMessage(H_VOLUME_MOUNT, vol).sendToTarget();
            }

        } else if (vol.type == VolumeInfo.TYPE_PUBLIC) {
            // TODO: only look at first public partition
            if (Objects.equals(StorageManager.UUID_PRIMARY_PHYSICAL, mPrimaryStorageUuid)
                    && vol.disk.isDefaultPrimary()) {
                Slog.v(TAG, "Found primary storage at " + vol);
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_PRIMARY;
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
            }

            // Adoptable public disks are visible to apps, since they meet
            // public API requirement of being in a stable location.
            if (vol.disk.isAdoptable()) {
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
            }

            vol.mountUserId = mCurrentUserId;
            mHandler.obtainMessage(H_VOLUME_MOUNT, vol).sendToTarget();

        } else if (vol.type == VolumeInfo.TYPE_PRIVATE) {
            mHandler.obtainMessage(H_VOLUME_MOUNT, vol).sendToTarget();

        } else {
            Slog.d(TAG, "Skipping automatic mounting of " + vol);
        }
    }
```