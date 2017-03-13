Jack
========================================

### BB

```
[ 31% 14186/45738] Ensure Jack server is installed and started
Jack server already installed in "/root/.jack-server"
Server is already running
[ 31% 14206/45738] Building with Jack: out/target/common/obj/JAVA_LIBRARIES/core-all_intermediates/with-local/classes.dex
FAILED: /bin/bash out/target/common/obj/JAVA_LIBRARIES/core-all_intermediates/with-local/classes.dex.rsp
Picked up _JAVA_OPTIONS: -Xmx4096m
Picked up _JAVA_OPTIONS: -Xmx4096m
Communication error with Jack server (28). Try 'jack-diagnose'
ninja: build stopped: subcommand failed.
make: *** [ninja_wrapper] Error 1
```

### FIX

```
# Use 7GB RAM for Jack Server -1GB from 8GB
export JACK_SERVER_VM_ARGUMENTS="-Dfile.encoding=UTF-8 -XX:+TieredCompilation -Xmx7000m"
# Killing...
out/host/linux-x86/bin/jack-admin kill-server
# Starting...
out/host/linux-x86/bin/jack-admin start-server
```