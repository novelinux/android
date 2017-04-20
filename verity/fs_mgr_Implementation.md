# fs_mgr Implementation

```
fs_mgr_mount_all
 |
fs_mgr_setup_verity
 |
 +-> fec_open
 |
 +-> fec_verity_get_metadata
 |
 +-> fec_ecc_get_metadata
 |
 +-> create_verity_device
 |
 +-> get_verity_device_name
 |
 +-> load_verity_state
 |
 +-> verify_verity_signature
 |
 +-> load_verity_table -> format_verity_table
 |
 +-> load_verity_table -> format_legacy_verity_table
 |
 +-> resume_verity_table
 |
 +-> fs_mgr_set_blk_ro
```

## fs_mgr_mount_all

path: system/core/fs_mgr/fs_mgr.c
```
int fs_mgr_mount_all(struct fstab *fstab, int mount_mode)
{
        ...

        if ((fstab->recs[i].fs_mgr_flags & MF_VERIFY) && device_is_secure()) {
            int rc = fs_mgr_setup_verity(&fstab->recs[i]);
            if (device_is_debuggable() && rc == FS_MGR_SETUP_VERITY_DISABLED) {
                INFO("Verity disabled");
            } else if (rc != FS_MGR_SETUP_VERITY_SUCCESS) {
                ERROR("Could not set up verified partition, skipping!\n");
                continue;
            }
        }
        ...
}
```

## fs_mgr_setup_verity

### struct fec_verity_metadata

path: system/extra/libfec/include/fec/io.h
```
struct fec_verity_metadata {
    bool disabled;
    uint64_t data_size;
    uint8_t signature[RSANUMBYTES];
    uint8_t ecc_signature[RSANUMBYTES];
    const char *table;
    uint32_t table_length;
};
```

### struct verity_table_params

path: system/core/fs_mgr/fs_mgr_verity.cpp
```
struct verity_table_params {
    char *table;
    int mode;
    struct fec_ecc_metadata ecc;
    const char *ecc_dev;
};
```

### fec_open

```
int fs_mgr_setup_verity(struct fstab_rec *fstab)
{
    int retval = FS_MGR_SETUP_VERITY_FAIL;
    int fd = -1;
    char *verity_blk_name = NULL;
    struct fec_handle *f = NULL;
    struct fec_verity_metadata verity;
    struct verity_table_params params = { .table = NULL };

    alignas(dm_ioctl) char buffer[DM_BUF_SIZE];
    struct dm_ioctl *io = (struct dm_ioctl *) buffer;
    char *mount_point = basename(fstab->mount_point);

    if (fec_open(&f, fstab->blk_device, O_RDONLY, FEC_VERITY_DISABLE,
            FEC_DEFAULT_ROOTS) < 0) {
        ERROR("Failed to open '%s' (%s)\n", fstab->blk_device,
            strerror(errno));
        return retval;
    }
```

### fec_verity_get_metadata

```
    // read verity metadata
    if (fec_verity_get_metadata(f, &verity) < 0) {
        ERROR("Failed to get verity metadata '%s' (%s)\n", fstab->blk_device,
            strerror(errno));
        goto out;
    }

#ifdef ALLOW_ADBD_DISABLE_VERITY
    if (verity.disabled) {
        retval = FS_MGR_SETUP_VERITY_DISABLED;
        INFO("Attempt to cleanly disable verity - only works in USERDEBUG\n");
        goto out;
    }
#endif
```

### fec_ecc_get_metadata

```
    // read ecc metadata
    if (fec_ecc_get_metadata(f, &params.ecc) < 0) {
        params.ecc.valid = false;
    }

    params.ecc_dev = fstab->blk_device;
```

### create verity device

```
    // get the device mapper fd
    if ((fd = open("/dev/device-mapper", O_RDWR)) < 0) {
        ERROR("Error opening device mapper (%s)\n", strerror(errno));
        goto out;
    }

    // create the device
    if (create_verity_device(io, mount_point, fd) < 0) {
        ERROR("Couldn't create verity device!\n");
        goto out;
    }
```

### get_verity_device_name

```
    // get the name of the device file
    if (get_verity_device_name(io, mount_point, fd, &verity_blk_name) < 0) {
        ERROR("Couldn't get verity device number!\n");
        goto out;
    }
```

### load_verity_state

```
    if (load_verity_state(fstab, &params.mode) < 0) {
        /* if accessing or updating the state failed, switch to the default
         * safe mode. This makes sure the device won't end up in an endless
         * restart loop, and no corrupted data will be exposed to userspace
         * without a warning. */
        params.mode = VERITY_MODE_EIO;
    }

    if (!verity.table) {
        goto out;
    }

    params.table = strdup(verity.table);
    if (!params.table) {
        goto out;
    }
```

### verify_verity_signature

```
    // verify the signature on the table
    if (verify_verity_signature(verity) < 0) {
        if (params.mode == VERITY_MODE_LOGGING) {
            // the user has been warned, allow mounting without dm-verity
            retval = FS_MGR_SETUP_VERITY_SUCCESS;
            goto out;
        }

        // invalidate root hash and salt to trigger device-specific recovery
        if (invalidate_table(params.table, verity.table_length) < 0) {
            goto out;
        }
    }

    INFO("Enabling dm-verity for %s (mode %d)\n", mount_point, params.mode);

    if (fstab->fs_mgr_flags & MF_SLOTSELECT) {
        // Update the verity params using the actual block device path
        update_verity_table_blk_device(fstab->blk_device, &params.table);
    }
```

### load_verity_table -> format_verity_table

```
    // load the verity mapping table
    if (load_verity_table(io, mount_point, verity.data_size, fd, &params,
            format_verity_table) == 0) {
        goto loaded;
    }

    if (params.ecc.valid) {
        // kernel may not support error correction, try without
        INFO("Disabling error correction for %s\n", mount_point);
        params.ecc.valid = false;

        if (load_verity_table(io, mount_point, verity.data_size, fd, &params,
                format_verity_table) == 0) {
            goto loaded;
        }
    }
```

### load_verity_table -> format_legacy_verity_table

```
    // try the legacy format for backwards compatibility
    if (load_verity_table(io, mount_point, verity.data_size, fd, &params,
            format_legacy_verity_table) == 0) {
        goto loaded;
    }

    if (params.mode != VERITY_MODE_EIO) {
        // as a last resort, EIO mode should always be supported
        INFO("Falling back to EIO mode for %s\n", mount_point);
        params.mode = VERITY_MODE_EIO;

        if (load_verity_table(io, mount_point, verity.data_size, fd, &params,
                format_legacy_verity_table) == 0) {
            goto loaded;
        }
    }

    ERROR("Failed to load verity table for %s\n", mount_point);
    goto out;
```

### resume_verity_table

```
loaded:

    // activate the device
    if (resume_verity_table(io, mount_point, fd) < 0) {
        goto out;
    }
```

### fs_mgr_set_blk_ro

```
    // mark the underlying block device as read-only
    fs_mgr_set_blk_ro(fstab->blk_device);

    // assign the new verity block device as the block device
    free(fstab->blk_device);
    fstab->blk_device = verity_blk_name;
    verity_blk_name = 0;

    // make sure we've set everything up properly
    if (test_access(fstab->blk_device) < 0) {
        goto out;
    }

    retval = FS_MGR_SETUP_VERITY_SUCCESS;

out:
    if (fd != -1) {
        close(fd);
    }

    fec_close(f);
    free(params.table);
    free(verity_blk_name);

    return retval;
}
```

## load_verity_table

```
static int load_verity_table(struct dm_ioctl *io, char *name, uint64_t device_size, int fd,
        const struct verity_table_params *params, format_verity_table_func format)
{
    char *verity_params;
    char *buffer = (char*) io;
    size_t bufsize;

    verity_ioctl_init(io, name, DM_STATUS_TABLE_FLAG);

    struct dm_target_spec *tgt = (struct dm_target_spec *) &buffer[sizeof(struct dm_ioctl)];

    // set tgt arguments
    io->target_count = 1;
    tgt->status = 0;
    tgt->sector_start = 0;
    tgt->length = device_size / 512;
    strcpy(tgt->target_type, "verity");

    // build the verity params
    verity_params = buffer + sizeof(struct dm_ioctl) + sizeof(struct dm_target_spec);
    bufsize = DM_BUF_SIZE - (verity_params - buffer);

    if (!format(verity_params, bufsize, params)) {
        ERROR("Failed to format verity parameters\n");
        return -1;
    }

    INFO("loading verity table: '%s'", verity_params);

    // set next target boundary
    verity_params += strlen(verity_params) + 1;
    verity_params = (char*)(((unsigned long)verity_params + 7) & ~8);
    tgt->next = verity_params - buffer;

    // send the ioctl to load the verity table
    if (ioctl(fd, DM_TABLE_LOAD, io)) {
        ERROR("Error loading verity table (%s)\n", strerror(errno));
        return -1;
    }

    return 0;
}
```