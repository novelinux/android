# simpleperf

## simpleperf stat

面对一个问题程序，最好采用自顶向下的策略。先整体看看该程序运行时各种统计事件的大概，再针对某些方向深入细节。而不要一下子扎进琐碎细节，会一叶障目的。

有些程序慢是因为计算量太大，其多数时间都应该在使用 CPU 进行计算，这叫做 CPU bound 型；有些程序慢是因为过多的 IO，这种时候其 CPU 利用率应该不高，这叫做 IO bound 型；对于 CPU bound 程序的调优和 IO bound 的调优是不同的。

simpleerf stat 应该是您最先使用的一个工具。它通过概括精简的方式提供被调试程序运行的整体情况和汇总数据。

simpleperf是用来统计基本事件的工具，如下：

```
/data/local/tmp # ./simpleperf stat --app com.example.simpleperf.simpleperfexamplewithnative
simpleperf I environment.cpp:619] Waiting for process of app com.example.simpleperf.simpleperfexamplewithnative
simpleperf I environment.cpp:611] Got process 15028 for package com.example.simpleperf.simpleperfexamplewithnative
^CPerformance counter statistics:

    17,286,418,646  cpu-cycles                #                     (50%)
     1,174,542,627  stalled-cycles-frontend   #                     (50%)
       294,929,345  stalled-cycles-backend    #                     (50%)
    41,009,980,868  instructions              #                     (50%)
     7,002,129,484  branch-instructions       #                     (50%)
         2,446,538  branch-misses             #                     (50%)
  12556.281896(ms)  task-clock                # 1.011305 cpus used  (100%)
               701  context-switches          # 55.829 /sec         (100%)
             4,959  page-faults               # 394.942 /sec        (100%)

Total test time: 12.415914 seconds.
```

上面告诉我们，app simple perf是一个 CPU bound 型，因为 task-clock超过了1。


## commands

```
$ ./simpleperf  record -e major-faults ./hello hello world
```

## app profiler

```
$ app_profiler.py -p com.example.simpleperf.simpleperfexamplewithnative -r "-e major-faults -f 1000 --duration 10 --call-graph fp"   -a .MixActivity  -lib ./build/intermediates/cmake/debug/obj/arm64-v8a/ 2>&1 | tee app_profile.log
```
