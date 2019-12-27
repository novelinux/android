## simpleperf

### commands

```
$ ./simpleperf  record -e major-faults ./hello hello world 

$ app_profiler.py -p com.example.simpleperf.simpleperfexamplewithnative -r "-e major-faults -f 1000 --duration 10 --call-graph fp"   -a .MixActivity  -lib ./build/intermediates/cmake/debug/obj/arm64-v8a/ 2>&1 | tee app_profile.log
```