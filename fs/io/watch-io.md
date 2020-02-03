## 1 系统级 IO 监控

iostat

```
[root@xxxx_wan360_game ~]# iostat -xdm 1
Linux 2.6.32-358.el6.x86_64 (xxxx_wan360_game) 	12/06/2016 	_x86_64_	(8 CPU)

Device:         rrqm/s   wrqm/s     r/s     w/s    rMB/s    wMB/s avgrq-sz avgqu-sz   await  svctm  %util
xvdep1            0.00     0.00    0.01    1.31     0.00     0.02    31.35     0.00    1.63   0.06   0.01

Device:         rrqm/s   wrqm/s     r/s     w/s    rMB/s    wMB/s avgrq-sz avgqu-sz   await  svctm  %util
xvdep1            0.00     0.00    0.00    0.00     0.00     0.00     0.00     0.00    0.00   0.00   0.00

Device:         rrqm/s   wrqm/s     r/s     w/s    rMB/s    wMB/s avgrq-sz avgqu-sz   await  svctm  %util
xvdep1            0.00     0.00    0.00    0.00     0.00     0.00     0.00     0.00    0.00   0.00   0.00

Device:         rrqm/s   wrqm/s     r/s     w/s    rMB/s    wMB/s avgrq-sz avgqu-sz   await  svctm  %util
xvdep1            0.00     0.00    0.00    3.00     0.00     0.01     8.00     0.00    0.00   0.00   0.00
```

### iostat 选项

```
-x     Display extended statistics.
This option works with post 2.5 kernels since it needs /proc/diskstats file or a mounted sysfs to get the statistics.
This option may also work with older kernels (e.g. 2.4) only if extended statistics are available in /proc/partitions
(the kernel needs to be patched for that).

-d     Display the device utilization report.

-m     Display statistics in megabytes per second instead of blocks or kilobytes per second.
Data displayed are valid only with kernels 2.4 and later.


rrqm/s
       The number of read requests merged per second that were queued to the device.
	 排队到设备时，每秒合并的读请求数量

wrqm/s
       The number of write requests merged per second that were queued to the device.
         排队到设备时，每秒合并的写请求数量

r/s
       The number of read requests that were issued to the device per second.
	 每秒发送给设备的读请求数量

w/s
       The number of write requests that were issued to the device per second.
	 每秒发送给设备的的写请求数量

rMB/s
       The number of megabytes read from the device per second.
	 每秒从设备中读取多少 MBs

wMB/s
       The number of megabytes written to the device per second.
	 每秒往设备中写入多少 MBs


avgrq-sz
	The average size (in sectors) of the requests that were issued to the device.
	分发给设备的请求的平均大小（以扇区为单位）
	磁盘扇区是磁盘的物理属性，它是磁盘设备寻址的最小单元，磁盘扇区大小可以用 fdisk -l 命令查看
	另外，常说的“块”（Block）是文件系统的抽象，不是磁盘本身的属性。
	另外一种说明：
	提交给驱动层的 IO 请求大小，一般不小于 4K，不大于 max(readahead_kb, max_sectors_kb)
	可用于判断当前的 IO 模式，一般情况下，尤其是磁盘繁忙时，越大代表顺序，越小代表随机

avgqu-sz
	The average queue length of the requests that were issued to the device.
	分发给设备的请求的平均队列长度

await
	The average time (in milliseconds) for I/O requests issued to the device to be served.
	This includes the time spent by the requests in queue and the time spent servicing them.
	分发给设备的 I/O 请求的平均响应时间（单位是毫秒）
	这个时间包含了花在请求在队列中的时间和服务于请求的时间
	另外一种说明：
	每一个 I/O 请求的处理的平均时间（单位是毫秒）。这里可以理解为 I/O 的响应时间。
	一般地，系统 I/O 响应时间应该低于 5ms，如果大于 10ms 就比较大了。

svctm
	The average service time (in milliseconds) for I/O requests that were issued to the device.
	Warning! Do not trust this field any more. This field will be removed in a future sysstat version.
	分发给设备的 I/O 请求的平均服务时间。（单位是毫秒）
	警告！不要再相信这列值了。这一列将会在一个将来的 sysstat 版本中移除。
	另外一种说明：
	一次 IO 请求的服务时间，对于单块盘，完全随机读时，基本在 7ms 左右，即寻道 + 旋转延迟时间

%util
	Percentage of elapsed time during which I/O requests were issued to the device
	(bandwidth utilization for the device). Device saturation occurs when this value is close to 100%.
	 分发给设备的 I/O 请求的运行时间所占的百分比。（设备的带宽利用率）
	 设备饱和会发生在这个值接近 100%。
	 另外一种说明：
	 代表磁盘繁忙程度。100% 表示磁盘繁忙，0% 表示磁盘空闲。但是注意，磁盘繁忙不代表磁盘(带宽)利用率高。
	 在统计时间内所有处理 I/O 时间，除以总共统计时间。
	 例如，如果统计间隔 1 秒，该设备有 0.8 秒在处理 I/O，而 0.2 秒闲置，那么该设备的 %util = 0.8/1 = 80%，
	 所以该参数暗示了设备的繁忙程度。一般地，如果该参数是 100% 表示设备已经接近满负荷运行了
	 （当然如果是多磁盘，即使 %util 是 100%，因为磁盘的并发能力，所以磁盘使用未必就到了瓶颈）。

%iowait
	Show the percentage of time that the CPU or CPUs were idle during
	which the system had an outstanding disk I/O request.
	 显示当系统有一个显著的磁盘 I/O 请求期间，CPU 空闲时间的百分比。
```

总结：

iostat 统计的是通用块层经过合并(rrqm/s, wrqm/s)后，直接向设备提交的 IO 数据，可以反映系统整体的 IO 状况，
但是有以下 2 个缺点:

1. 距离业务层比较遥远，跟代码中的 write，read 不对应(由于系统预读 + PageCache + IO 调度算法等因素，也很难对应)
2. 是系统级，没办法精确到进程，比如只能告诉你现在磁盘很忙，但是没办法告诉你是谁在忙，在忙什么

另一资料的总结：

如果 %iowait 的值过高，表示磁盘存在 I/O 瓶颈。
如果 %util 接近 100%，说明产生的 I/O 请求太多，I/O 系统已经满负荷，该磁盘可能存在瓶颈。
如果 svctm 比较接近 await，说明 I/O 几乎没有等待时间；
如果 await 远大于 svctm，说明 I/O 队列太长，I/O 响应太慢，则需要进行必要优化。
如果 avgqu-sz 比较大，也表示有大量 IO 在等待。

## 2 进程级 IO 监控

iotop 和 pidstat
iotop 顾名思义, IO 版的 top
pidstat 顾名思义, 统计进程(pid)的 stat，进程的 stat 自然包括进程的 IO 状况
这两个命令，都可以按进程统计 IO 状况，因此可以回答你以下二个问题：

当前系统哪些进程在占用 IO，百分比是多少?
占用 IO 的进程是在读？还是在写？读写量是多少？
pidstat 参数很多，根据需要使用


```
[root@xxxx_wan360_game ~]# pidstat -d 1		# 只显示 IO
Linux 2.6.32-358.el6.x86_64 (xxxx_wan360_game) 	12/06/2016 	_x86_64_	(8 CPU)

05:28:57 PM       PID   kB_rd/s   kB_wr/s kB_ccwr/s  Command
05:28:58 PM        50      0.00      4.00      0.00  sync_supers
05:28:58 PM       627      0.00      8.00      0.00  flush-202:65
05:28:58 PM      3852      0.00      8.00      0.00  cente_s0001
05:28:58 PM      3860      0.00      4.00      0.00  game_s0001
05:28:58 PM      3864      0.00      4.00      0.00  game_s0001
05:28:58 PM      3868      0.00      4.00      0.00  game_s0001
05:28:58 PM      3876      0.00      4.00      0.00  gate_s0001
05:28:58 PM      3880      0.00      4.00      0.00  gate_s0001

05:28:58 PM       PID   kB_rd/s   kB_wr/s kB_ccwr/s  Command

05:28:59 PM       PID   kB_rd/s   kB_wr/s kB_ccwr/s  Command
05:29:00 PM     23922      0.00     20.00      0.00  filebeat
```

```
# pidstat -u -r -d -t 1
# -u CPU 使用率
# -r 缺页及内存信息
# -d IO 信息
# -t 以线程为统计单位
# 1  1 秒统计一次

[root@xxxx_wan360_game ~]# pidstat -u -r -d -t 1
Linux 2.6.32-358.el6.x86_64 (xxxx_wan360_game) 	12/06/2016 	_x86_64_	(8 CPU)

05:32:11 PM      TGID       TID    %usr %system  %guest    %CPU   CPU  Command
05:32:12 PM      3856         -    3.74    0.93    0.00    4.67     5  game_s0001
05:32:12 PM         -      3856    4.67    0.93    0.00    5.61     5  |__game_s0001
05:32:12 PM         -      3922    0.93    0.00    0.00    0.93     2  |__game_s0001
05:32:12 PM      3880         -    0.00    0.93    0.00    0.93     3  gate_s0001
05:32:12 PM         -      3908    0.00    0.93    0.00    0.93     3  |__gate_s0001
05:32:12 PM      6832         -    1.87    4.67    0.00    6.54     4  pidstat
05:32:12 PM         -      6832    1.87    4.67    0.00    6.54     4  |__pidstat

05:32:11 PM      TGID       TID  minflt/s  majflt/s     VSZ    RSS   %MEM  Command
05:32:12 PM      6803         -      5.61      0.00    4124    796   0.00  iostat
05:32:12 PM         -      6803      5.61      0.00    4124    796   0.00  |__iostat
05:32:12 PM      6832         -   1321.50      0.00  101432   1280   0.01  pidstat
05:32:12 PM         -      6832   1321.50      0.00  101432   1280   0.01  |__pidstat
05:32:12 PM      8391         -      0.93      0.00   17992   1176   0.01  zabbix_agentd
05:32:12 PM         -      8391      0.93      0.00   17992   1176   0.01  |__zabbix_agentd
05:32:12 PM      8392         -      2.80      0.00   20064   1320   0.01  zabbix_agentd
05:32:12 PM         -      8392      2.80      0.00   20064   1320   0.01  |__zabbix_agentd

05:32:11 PM      TGID       TID   kB_rd/s   kB_wr/s kB_ccwr/s  Command
05:32:12 PM      1894         -      0.00      3.74      0.00  mysqld
05:32:12 PM         -      1923      0.00      3.74      0.00  |__mysqld
```

总结:

进程级 IO 监控：

可以回答系统级 IO 监控不能回答的 2 个问题
距离业务层相对较近(例如，可以统计进程的读写量)
但是也没有办法跟业务层的 read, write 联系在一起，同时颗粒度较粗，没有办法告诉你，当前进程读写了哪些文件？耗时？大小？

## 3. 业务级 IO 监控

ioprofile
ioprofile 命令本质上是 lsof + strace ioprofile 可以回答你以下三个问题:

当前进程某时间内,在业务层面读写了哪些文件(read, write)？
读写次数是多少？(read, write 的调用次数)
读写数据量多少？(read, write 的 byte 数)
注: ioprofile 仅支持多线程程序,对单线程程序不支持. 对于单线程程序的 IO 业务级分析，strace 足以。

总结： ioprofile 本质上是 strace，因此可以看到 read，write 的调用轨迹，可以做业务层的 IO 分析(mmap 方式无能为力)

## 4. 文件级 IO 监控

文件级 IO 监控可以配合/补充”业务级和进程级” IO 分析
文件级 IO 分析，主要针对单个文件，回答当前哪些进程正在对某个文件进行读写操作

lsof 或者 ls /proc/pid/fd
inodewatch.stp
lsof 告诉你当前文件由哪些进程打开

```
[root@xxxx_wan360_game ~]# lsof ./		# 当前目录当前由 bash 和 lsof 进程打开
COMMAND   PID USER   FD   TYPE DEVICE SIZE/OFF     NODE NAME
lsof     8932 root  cwd    DIR 202,65     4096 33652737 .
lsof     8938 root  cwd    DIR 202,65     4096 33652737 .
bash    16678 root  cwd    DIR 202,65     4096 33652737 .
```

lsof 命令只能回答静态的信息，并且“打开”并不一定“读取”，
对于 cat，echo 这样的命令，打开和读取都是瞬间的，lsof 很难捕捉 可以用 inodewatch.stp 来弥补
