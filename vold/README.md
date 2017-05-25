# vold

* LOGS

```
01-01 00:28:18.062   463   463 I vold    : Vold 3.0 (the awakening) firing up
01-01 00:28:18.063   463   463 V vold    : Detected support for: ext4 vfat
```

## vold.rc

```
service vold /system/bin/vold \
        --blkid_context=u:r:blkid:s0 --blkid_untrusted_context=u:r:blkid_untrusted:s0 \
        --fsck_context=u:r:fsck:s0 --fsck_untrusted_context=u:r:fsck_untrusted:s0
    class core
    socket vold stream 0660 root mount
    socket cryptd stream 0660 root mount
    ioprio be 2
    writepid /dev/cpuset/foreground/tasks
```

## main

path: system/vold

```
int main(int argc, char** argv) {
    setenv("ANDROID_LOG_TAGS", "*:v", 1);
    android::base::InitLogging(argv, android::base::LogdLogger(android::base::SYSTEM));

    LOG(INFO) << "Vold 3.0 (the awakening) firing up";

    LOG(VERBOSE) << "Detected support for:"
            << (android::vold::IsFilesystemSupported("ext4") ? " ext4" : "")
            << (android::vold::IsFilesystemSupported("f2fs") ? " f2fs" : "")
            << (android::vold::IsFilesystemSupported("vfat") ? " vfat" : "");

    VolumeManager *vm;
    CommandListener *cl;
    CryptCommandListener *ccl;
    NetlinkManager *nm;

    parse_args(argc, argv);

    sehandle = selinux_android_file_context_handle();
    if (sehandle) {
        selinux_android_set_sehandle(sehandle);
    }

    // Quickly throw a CLOEXEC on the socket we just inherited from init
    fcntl(android_get_control_socket("vold"), F_SETFD, FD_CLOEXEC);
    fcntl(android_get_control_socket("cryptd"), F_SETFD, FD_CLOEXEC);

    mkdir("/dev/block/vold", 0755);

    /* For when cryptfs checks and mounts an encrypted filesystem */
    klog_set_level(6);

    /* Create our singleton managers */
    if (!(vm = VolumeManager::Instance())) {
        LOG(ERROR) << "Unable to create VolumeManager";
        exit(1);
    }

    if (!(nm = NetlinkManager::Instance())) {
        LOG(ERROR) << "Unable to create NetlinkManager";
        exit(1);
    }

    if (property_get_bool("vold.debug", false)) {
        vm->setDebug(true);
    }

    cl = new CommandListener();
    ccl = new CryptCommandListener();
    vm->setBroadcaster((SocketListener *) cl);
    nm->setBroadcaster((SocketListener *) cl);

    if (vm->start()) {
        PLOG(ERROR) << "Unable to start VolumeManager";
        exit(1);
    }

    bool has_adoptable;

    if (process_config(vm, &has_adoptable)) {
        PLOG(ERROR) << "Error reading configuration... continuing anyways";
    }

    if (nm->start()) {
        PLOG(ERROR) << "Unable to start NetlinkManager";
        exit(1);
    }

    /*
     * Now that we're up, we can respond to commands
     */
    if (cl->startListener()) {
        PLOG(ERROR) << "Unable to start CommandListener";
        exit(1);
    }

    if (ccl->startListener()) {
        PLOG(ERROR) << "Unable to start CryptCommandListener";
        exit(1);
    }

    // This call should go after listeners are started to avoid
    // a deadlock between vold and init (see b/34278978 for details)
    property_set("vold.has_adoptable", has_adoptable ? "1" : "0");

    // Do coldboot here so it won't block booting,
    // also the cold boot is needed in case we have flash drive
    // connected before Vold launched
    coldboot("/sys/block");
    // Eventually we'll become the monitoring thread
    while(1) {
        pause();
    }

    LOG(ERROR) << "Vold exiting";
    exit(0);
}
```
