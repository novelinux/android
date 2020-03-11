# System tuning parameters for vmtouch

## Limits on memory locking

See your operating system's `mlock(2)` manual page since it probably describes the limitations on this system call which vmtouch uses.

### Linux

* **locked memory rlimit**: Processes typically have a limit on the number of memory that is locked. This can be raised with `ulimit -l` (see `RLIMIT_MEMLOCK` in [setrlimit(2)](http://linux.die.net/man/2/setrlimit)) if you are the super-user. Processes with the `CAP_IPC_LOCK` are not affected by this limit, and it can be raised for unprivileged processes by editing [limits.conf](http://linux.die.net/man/5/limits.conf).
* **vm.max_map_count**: This is a [sysctl](http://linux.die.net/man/8/sysctl) that controls the maximum number of VMAs (virtual memory areas) that a process can map. Since vmtouch needs a VMA for every file, this limits the number of files that can be locked by an individual vmtouch process.
* Since Linux 2.6.9 (?) there is no system-wide limit on the amount of locked memory.

### FreeBSD

* See [mlock(2)](https://www.freebsd.org/cgi/man.cgi?query=mlock&sektion=2&manpath=freebsd-release-ports)
* **sysctls**: see `vm.max_wired` and `vm.stats.vm.v_wire_count`
* **security.bsd.unprivileged_mlock**: Whether unprivileged users can lock memory

### OpenBSD

* Has both a per-process resource limit and a system-wide limit on locked memory, see [mlock(2)](http://man.openbsd.org/mlock.2)

### Solaris

On Solaris, the memory locked page limit can be set on per-project or per-zone basis, see resource-controls(5) man page for details. The limits can be manipulated and observed using prctl or zonecfg command, e.g.:

    $ prctl -n project.max-locked-memory -i process $$
    process: 4690: bash
    NAME    PRIVILEGE       VALUE    FLAG   ACTION                       RECIPIENT
    project.max-locked-memory
            usage               0B
            system          16.0EB    max   deny                                 -

Also, there is the PRIV_PROC_LOCK_MEMORY privilege which controls whether given process can lock memory.



## Network filesystems

### NFS

NFS appears to function as a normal filesystem from `vmtouch`'s perspective. The only issue is that crawls of large directories can take a long time.

### S3FS

On linux, S3FS is a FUSE-based filesystem driver that mounts your Amazon S3 buckets. When caching files it doesn't use the normal page cache for these files, but instead maintains a cache directory where the cached files and portions of files reside. `vmtouch` can be meaningfully used on these files in the cache directory, but not in the mount itself.

By default the cache directory is `$HOME/.fuse-s3fs-cache/` but this can be changed with the `cachedir` mount option is used. Also see the `preserve_cache` option if you wish the cache to persist between mountings.

### 9P

The 9P filesystem driver 9P2000 on linux doesn't by default use the filesystem cache. However, there is a mount option `fscache` which enables this. See github issue [#56](https://github.com/hoytech/vmtouch/issues/56). After this is enabled, `vmtouch` should work as normal.
