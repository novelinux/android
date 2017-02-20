# Android Debug

Debug技能是每一个开发工程师必备技能,是优秀工程师重要的衡量标准.

大致步骤:

* 1.收到反馈;
* 2.仔细分析反馈信息,通过反馈信息和对比测试细化问题.例如,初步将问题分成App, Framework还是底层kernel问题等;
* 3.能够稳定复现的问题,通过LOG信息不断的迭代缩小问题范围进行调试;
* 4.不能稳定复现的问题,尝试通过分析尽可能多的LOG去找到共性,提出假设,验证假设.

解决问题的过程就像在解决一道综合题,在解决问题过程中要保持耐性,思路清晰,符合逻辑,一边猜测,一边验证,
不断缩小范围,最终定位根本原因.

## Android Debug Tools

### adb

adb(Android Debug Bridge)是android系统中的一种命令行工具，通过它可以和Android
设备或模拟器用USB或socket进行通信.

```
$ adb -h
Android Debug Bridge version 1.0.36
Revision 302830efc153-android

 -a                            - directs adb to listen on all interfaces for a connection
 -d                            - directs command to the only connected USB device
                                 returns an error if more than one USB device is present.
 -e                            - directs command to the only running emulator.
                                 returns an error if more than one emulator is running.
 -s <specific device>          - directs command to the device or emulator with the given
                                 serial number or qualifier. Overrides ANDROID_SERIAL
                                 environment variable.
 -p <product name or path>     - simple product name like 'sooner', or
                                 a relative/absolute path to a product
                                 out directory like 'out/target/product/sooner'.
                                 If -p is not specified, the ANDROID_PRODUCT_OUT
                                 environment variable is used, which must
                                 be an absolute path.
 -H                            - Name of adb server host (default: localhost)
 -P                            - Port of adb server (default: 5037)
 devices [-l]                  - list all connected devices
                                 ('-l' will also list device qualifiers)
 connect <host>[:<port>]       - connect to a device via TCP/IP
                                 Port 5555 is used by default if no port number is specified.
 disconnect [<host>[:<port>]]  - disconnect from a TCP/IP device.
                                 Port 5555 is used by default if no port number is specified.
                                 Using this command with no additional arguments
                                 will disconnect from all connected TCP/IP devices.

device commands:
  adb push <local>... <remote>
                               - copy files/dirs to device
  adb pull [-a] <remote>... <local>
                               - copy files/dirs from device
                                 (-a preserves file timestamp and mode)
  adb sync [ <directory> ]     - copy host->device only if changed
                                 (-l means list but don't copy)
  adb shell [-e escape] [-n] [-Tt] [-x] [command]
                               - run remote shell command (interactive shell if no command given)
                                 (-e: choose escape character, or "none"; default '~')
                                 (-n: don't read from stdin)
                                 (-T: disable PTY allocation)
                                 (-t: force PTY allocation)
                                 (-x: disable remote exit codes and stdout/stderr separation)
  adb emu <command>            - run emulator console command
  adb logcat [ <filter-spec> ] - View device log
  adb forward --list           - list all forward socket connections.
                                 the format is a list of lines with the following format:
                                    <serial> " " <local> " " <remote> "\n"
  adb forward <local> <remote> - forward socket connections
                                 forward specs are one of:
                                   tcp:<port>
                                   localabstract:<unix domain socket name>
                                   localreserved:<unix domain socket name>
                                   localfilesystem:<unix domain socket name>
                                   dev:<character device name>
                                   jdwp:<process pid> (remote only)
  adb forward --no-rebind <local> <remote>
                               - same as 'adb forward <local> <remote>' but fails
                                 if <local> is already forwarded
  adb forward --remove <local> - remove a specific forward socket connection
  adb forward --remove-all     - remove all forward socket connections
  adb reverse --list           - list all reverse socket connections from device
  adb reverse <remote> <local> - reverse socket connections
                                 reverse specs are one of:
                                   tcp:<port>
                                   localabstract:<unix domain socket name>
                                   localreserved:<unix domain socket name>
                                   localfilesystem:<unix domain socket name>
  adb reverse --no-rebind <remote> <local>
                               - same as 'adb reverse <remote> <local>' but fails
                                 if <remote> is already reversed.
  adb reverse --remove <remote>
                               - remove a specific reversed socket connection
  adb reverse --remove-all     - remove all reversed socket connections from device
  adb jdwp                     - list PIDs of processes hosting a JDWP transport
  adb install [-lrtsdg] <file>
                               - push this package file to the device and install it
                                 (-l: forward lock application)
                                 (-r: replace existing application)
                                 (-t: allow test packages)
                                 (-s: install application on sdcard)
                                 (-d: allow version code downgrade (debuggable packages only))
                                 (-g: grant all runtime permissions)
  adb install-multiple [-lrtsdpg] <file...>
                               - push this package file to the device and install it
                                 (-l: forward lock application)
                                 (-r: replace existing application)
                                 (-t: allow test packages)
                                 (-s: install application on sdcard)
                                 (-d: allow version code downgrade (debuggable packages only))
                                 (-p: partial application install)
                                 (-g: grant all runtime permissions)
  adb uninstall [-k] <package> - remove this app package from the device
                                 ('-k' means keep the data and cache directories)
  adb bugreport [<path>]       - return all information from the device that should be included in a zipped bug report.
                                 If <path> is a file, the bug report will be saved as that file.
                                 If <path> is a directory, the bug report will be saved in that directory with the name provided by the device.
                                 If <path> is omitted, the bug report will be saved in the current directory with the name provided by the device.
                                 NOTE: if the device does not support zipped bug reports, the bug report will be output on stdout.
  adb backup [-f <file>] [-apk|-noapk] [-obb|-noobb] [-shared|-noshared] [-all] [-system|-nosystem] [<packages...>]
                               - write an archive of the device's data to <file>.
                                 If no -f option is supplied then the data is written
                                 to "backup.ab" in the current directory.
                                 (-apk|-noapk enable/disable backup of the .apks themselves
                                    in the archive; the default is noapk.)
                                 (-obb|-noobb enable/disable backup of any installed apk expansion
                                    (aka .obb) files associated with each application; the default
                                    is noobb.)
                                 (-shared|-noshared enable/disable backup of the device's
                                    shared storage / SD card contents; the default is noshared.)
                                 (-all means to back up all installed applications)
                                 (-system|-nosystem toggles whether -all automatically includes
                                    system applications; the default is to include system apps)
                                 (<packages...> is the list of applications to be backed up.  If
                                    the -all or -shared flags are passed, then the package
                                    list is optional.  Applications explicitly given on the
                                    command line will be included even if -nosystem would
                                    ordinarily cause them to be omitted.)

  adb restore <file>           - restore device contents from the <file> backup archive

  adb disable-verity           - disable dm-verity checking on USERDEBUG builds
  adb enable-verity            - re-enable dm-verity checking on USERDEBUG builds
  adb keygen <file>            - generate adb public/private key. The private key is stored in <file>,
                                 and the public key is stored in <file>.pub. Any existing files
                                 are overwritten.
  adb help                     - show this help message
  adb version                  - show version num

scripting:
  adb wait-for[-<transport>]-<state>
                               - wait for device to be in the given state:
                                 device, recovery, sideload, or bootloader
                                 Transport is: usb, local or any [default=any]
  adb start-server             - ensure that there is a server running
  adb kill-server              - kill the server if it is running
  adb get-state                - prints: offline | bootloader | device
  adb get-serialno             - prints: <serial-number>
  adb get-devpath              - prints: <device-path>
  adb remount                  - remounts the /system, /vendor (if present) and /oem (if present) partitions on the device read-write
  adb reboot [bootloader|recovery]
                               - reboots the device, optionally into the bootloader or recovery program.
  adb reboot sideload          - reboots the device into the sideload mode in recovery program (adb root required).
  adb reboot sideload-auto-reboot
                               - reboots into the sideload mode, then reboots automatically after the sideload regardless of the result.
  adb sideload <file>          - sideloads the given package
  adb root                     - restarts the adbd daemon with root permissions
  adb unroot                   - restarts the adbd daemon without root permissions
  adb usb                      - restarts the adbd daemon listening on USB
  adb tcpip <port>             - restarts the adbd daemon listening on TCP on the specified port

networking:
  adb ppp <tty> [parameters]   - Run PPP over USB.
 Note: you should not automatically start a PPP connection.
 <tty> refers to the tty for PPP stream. Eg. dev:/dev/omap_csmi_tty1
 [parameters] - Eg. defaultroute debug dump local notty usepeerdns

adb sync notes: adb sync [ <directory> ]
  <localdir> can be interpreted in several ways:

  - If <directory> is not specified, /system, /vendor (if present), /oem (if present) and /data partitions will be updated.

  - If it is "system", "vendor", "oem" or "data", only the corresponding partition
    is updated.

internal debugging:
  adb reconnect                  Kick current connection from host side and make it reconnect.
  adb reconnect device           Kick current connection from device side and make it reconnect.
environment variables:
  ADB_TRACE                    - Print debug information. A comma separated list of the following values
                                 1 or all, adb, sockets, packets, rwx, usb, sync, sysdeps, transport, jdwp
  ANDROID_SERIAL               - The serial number to connect to. -s takes priority over this if given.
  ANDROID_LOG_TAGS             - When used with the logcat option, only these debug tags are printed.
```

### fastboot

fastboot是Android线刷刷机工具,是通过fastboot协议使用USB同LK(littel kernel, Bootloader)
进行通讯的工具,通常我们也会使用fastboot刷入一个支持root的bootimage来打开adb进行进一步
问题分析.

```
$ fastboot -h
usage: fastboot [ <option> ] <command>

commands:
  update <filename>                        Reflash device from update.zip.
                                           Sets the flashed slot as active.
  flashall                                 Flash boot, system, vendor, and --
                                           if found -- recovery. If the device
                                           supports slots, the slot that has
                                           been flashed to is set as active.
                                           Secondary images may be flashed to
                                           an inactive slot.
  flash <partition> [ <filename> ]         Write a file to a flash partition.
  flashing lock                            Locks the device. Prevents flashing.
  flashing unlock                          Unlocks the device. Allows flashing
                                           any partition except
                                           bootloader-related partitions.
  flashing lock_critical                   Prevents flashing bootloader-related
                                           partitions.
  flashing unlock_critical                 Enables flashing bootloader-related
                                           partitions.
  flashing get_unlock_ability              Queries bootloader to see if the
                                           device is unlocked.
  flashing get_unlock_bootloader_nonce     Queries the bootloader to get the
                                           unlock nonce.
  flashing unlock_bootloader <request>     Issue unlock bootloader using request.
  flashing lock_bootloader                 Locks the bootloader to prevent
                                           bootloader version rollback.
  erase <partition>                        Erase a flash partition.
  format[:[<fs type>][:[<size>]] <partition>
                                           Format a flash partition. Can
                                           override the fs type and/or size
                                           the bootloader reports.
  getvar <variable>                        Display a bootloader variable.
  set_active <slot>                        Sets the active slot. If slots are
                                           not supported, this does nothing.
  boot <kernel> [ <ramdisk> [ <second> ] ] Download and boot kernel.
  flash:raw boot <kernel> [ <ramdisk> [ <second> ] ]
                                           Create bootimage and flash it.
  devices [-l]                             List all connected devices [with
                                           device paths].
  continue                                 Continue with autoboot.
  reboot [bootloader]                      Reboot device [into bootloader].
  reboot-bootloader                        Reboot device into bootloader.
  help                                     Show this help message.

options:
  -w                                       Erase userdata and cache (and format
                                           if supported by partition type).
  -u                                       Do not erase partition before
                                           formatting.
  -s <specific device>                     Specify a device. For USB, provide either
                                           a serial number or path to device port.
                                           For ethernet, provide an address in the
                                           form <protocol>:<hostname>[:port] where
                                           <protocol> is either tcp or udp.
  -p <product>                             Specify product name.
  -c <cmdline>                             Override kernel commandline.
  -i <vendor id>                           Specify a custom USB vendor id.
  -b, --base <base_addr>                   Specify a custom kernel base
                                           address (default: 0x10000000).
  --kernel-offset                          Specify a custom kernel offset.
                                           (default: 0x00008000)
  --ramdisk-offset                         Specify a custom ramdisk offset.
                                           (default: 0x01000000)
  --tags-offset                            Specify a custom tags offset.
                                           (default: 0x00000100)
  -n, --page-size <page size>              Specify the nand page size
                                           (default: 2048).
  -S <size>[K|M|G]                         Automatically sparse files greater
                                           than 'size'. 0 to disable.
  --slot <slot>                            Specify slot name to be used if the
                                           device supports slots. All operations
                                           on partitions that support slots will
                                           be done on the slot specified.
                                           'all' can be given to refer to all slots.
                                           'other' can be given to refer to a
                                           non-current slot. If this flag is not
                                           used, slotted partitions will default
                                           to the current active slot.
  -a, --set-active[=<slot>]                Sets the active slot. If no slot is
                                           provided, this will default to the value
                                           given by --slot. If slots are not
                                           supported, this sets the current slot
                                           to be active. This will run after all
                                           non-reboot commands.
  --skip-secondary                         Will not flash secondary slots when
                                           performing a flashall or update. This
                                           will preserve data on other slots.
  --wipe-and-use-fbe                       On devices which support it,
                                           erase userdata and cache, and
                                           enable file-based encryption
  --unbuffered                             Do not buffer input or output.
  --version                                Display version.
  -h, --help                               show this message.
```

## Android Log

### bugreport

bugreport里面包含了各种log信息(机器版本, CPU, 内存, logcat, dmesg等等),大部分log也可以通过直接运行相关的程序来直接获得.

```
$ adb bugreport 2>&1 | tee bugreport.log
```

### logcat

logcat是Android中一个命令行工具，可以用于得到Framework, 某些Native进程和APP的Log信息。
通常是我们使用最多的命令之一.

```
$ adb logcat --help
Usage: logcat [options] [filterspecs]
options include:
  -s              Set default filter to silent.
                  Like specifying filterspec '*:S'
  -f <filename>   Log to file. Default is stdout
  -r <kbytes>     Rotate log every kbytes. Requires -f
  -n <count>      Sets max number of rotated logs to <count>, default 4
  -v <format>     Sets the log print format, where <format> is:

                      brief color long printable process raw tag thread
                      threadtime time usec

  -D              print dividers between each log buffer
  -c              clear (flush) the entire log and exit
  -d              dump the log and then exit (don't block)
  -t <count>      print only the most recent <count> lines (implies -d)
  -t '<time>'     print most recent lines since specified time (implies -d)
  -T <count>      print only the most recent <count> lines (does not imply -d)
  -T '<time>'     print most recent lines since specified time (not imply -d)
                  count is pure numerical, time is 'MM-DD hh:mm:ss.mmm'
  -g              get the size of the log's ring buffer and exit
  -L              dump logs from prior to last reboot
  -b <buffer>     Request alternate ring buffer, 'main', 'system', 'radio',
                  'events', 'crash' or 'all'. Multiple -b parameters are
                  allowed and results are interleaved. The default is
                  -b main -b system -b crash.
  -B              output the log in binary.
  -S              output statistics.
  -G <size>       set size of log ring buffer, may suffix with K or M.
  -p              print prune white and ~black list. Service is specified as
                  UID, UID/PID or /PID. Weighed for quicker pruning if prefix
                  with ~, otherwise weighed for longevity if unadorned. All
                  other pruning activity is oldest first. Special case ~!
                  represents an automatic quicker pruning for the noisiest
                  UID as determined by the current statistics.
  -P '<list> ...' set prune white and ~black list, using same format as
                  printed above. Must be quoted.

filterspecs are a series of
  <tag>[:priority]

where <tag> is a log component tag (or * for all) and priority is:
  V    Verbose (default for <tag>)
  D    Debug (default for '*')
  I    Info
  W    Warn
  E    Error
  F    Fatal
  S    Silent (suppress all output)

'*' by itself means '*:D' and <tag> by itself means <tag>:V.
If no '*' filterspec or -s on command line, all filter defaults to '*:V'.
eg: '*:S <tag>' prints only <tag>, '<tag>:S' suppresses all <tag> log messages.

If not specified on the command line, filterspec is set from ANDROID_LOG_TAGS.

If not specified with -v on command line, format is set from ANDROID_PRINTF_LOG
or defaults to "threadtime".
```

* log levels

* VERBOSE 类型调试信息，verbose啰嗦的意思
* DEBUG 类型调试信息, debug调试信息
* INFO 类型调试信息, 一般提示性的消息information
* WARN 类型调试信息，warning警告类型信息
* ERROR 类型调试信息，错误信息

可根据Logcat提供的过滤器来和信息类型来决定使用Log的那个方法添加哪类调试信息:

* V：不过滤输出所有调试信息 包括 VERBOSE、DEBUG、INFO、WARN、ERROR
* D：debug过滤器，输出DEBUG、INFO、WARN、ERROR调试信息
* I：info过滤器，输出INFO、WARN、ERROR调试信息
* W：waring过滤器，输出WARN和ERROR调试信息
* E：error过滤器，只输出ERROR调试信息

### dmesg

dmesg通常是我们用来查看kernel, init进程等log相关信息.

```
$ adb shell dmesg
```

## Breakpoint Debug Tools

### Java - (eclipse vs Android Studio)

http://training.pt.miui.com/posts/107

### Native - (gdb)

http://training.pt.miui.com/posts/108
