## simpleperf

### commands

```
$ ./simpleperf  record -e major-faults ./hello hello world 

$ app_profiler.py -p com.example.simpleperf.simpleperfexamplewithnative -r "-e major-faults -f 1000 --duration 10 --call-graph fp"   -a .MixActivity  -lib ./build/intermediates/cmake/debug/obj/arm64-v8a/ 2>&1 | tee app_profile.log
```

### 查找执行时间最长的共享库

您可以运行此命令来查看哪些 .so 文件占用了最大的执行时间百分比（基于 CPU 周期数）。启动性能分析会话时，首先运行此命令是个不错的选择。

```
    $ simpleperf report --sort dso
```    

### 查找执行时间最长的函数

当您确定占用最多执行时间的共享库后，就可以运行此命令来查看执行该 .so 文件的函数所用时间的百分比。

```
    $ simpleperf report --dsos library.so --sort symbol
```    

### 查找线程中所用时间的百分比

.so 文件中的执行时间可以跨多个线程分配。您可以运行此命令来查看每个线程所用时间的百分比。

```
    $ simpleperf report --sort tid,comm
```    

### 查找对象模块中所用时间的百分比

在找到占用大部分执行时间的线程之后，可以使用此命令来隔离在这些线程上占用最长执行时间的对象模块。

```
    $ simpleperf report --tids threadID --sort dso
```    

### 了解函数调用的相关性

调用图可直观呈现 Simpleperf 在对会话进行性能剖析期间记录的堆栈轨迹。在开始记录调用图信息之前，请参阅记录注意事项。

您可以使用 report -g 命令打印调用图，以查看其他函数调用的函数。这有助于确定是某个函数本身运行缓慢还是因为它调用的一个或多个函数运行较慢。

```
    $ simpleperf report -g
```    

您还可以使用 Python 脚本 report.py -g 来启动显示函数的交互式工具。您可以点击每个函数，查看它的子函数所用的时间。

https://developer.android.com/ndk/guides/simpleperf?hl=zh-cn