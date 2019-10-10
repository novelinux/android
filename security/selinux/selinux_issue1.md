一. 案例

```
1.源码：
    /** @hide */

    private TelephonyManager(int slotId) {
        mContext = null;
        mSlotId = slotId;
        if (sRegistry == null) {
            if (sRegistry == null) {
                 sRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                               "telephony.registry"));
            }
        }
    }

    private static TelephonyManager[] sInstance = new TelephonyManager[MultiSimManager.MAX_SLOT_COUNT + 1];

    static {
        for (int i = 0; i < sInstance.length; ++i) {
            sInstance[i] = new TelephonyManager(i);
        }
    }
```

这段代码很简单，就是在static代码块中new TelephonyManager对象，而在TelephonyManager的构造函数中调用ServiceManager的getService方法来获取telephony.registry服务的代理对象.

2.影响：
这段看似很简单的代码曾导致miui系统无法正常启动，连续两天无法出包，导致数以十计的工程师因此无法正常工作，花费系统组工程师大量时间来排查原因.

二. 案例分析
1.根据抓取log初步排查：

```
11-06 12:36:29.519  1150  1150 E JavaBinder: !!! FAILED BINDER TRANSACTION !!!
11-06 12:36:29.519  1150  1150 E ServiceManager: error in addService
11-06 12:36:29.519  1150  1150 E ServiceManager: android.os.TransactionTooLargeException
11-06 12:36:29.519  1150  1150 E ServiceManager:     at android.os.BinderProxy.transact(Native Method)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at android.os.ServiceManagerProxy.addService(ServiceManagerNative.java:150)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at android.os.ServiceManager.addService(ServiceManager.java:72)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at com.android.server.ServerThread.initAndLoop(SystemServer.java:215)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at com.android.server.SystemServer.main(SystemServer.java:1285)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at java.lang.reflect.Method.invokeNative(Native Method)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at java.lang.reflect.Method.invoke(Method.java:515)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:807)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:623)
11-06 12:36:29.519  1150  1150 E ServiceManager:     at dalvik.system.NativeStart.main(Native Method)
```

查 看相关堆栈信息初步这是system_server进程在add power服务的时候失败. 经过查看相关change, 在无法打包的时间点没有任何工程师进过有关Power和SystemServer相关的改动，故怀疑在add power服务之前就应该有服务died.

在 system_server进程中add的第一个服务是sensorservice, 通过打印log发现，在add sensorservice服务的时候就已经出现了错误，但是查看sensorservice相关的实现，同样没有任何相关的改动. 再次怀疑可能是别的模块相关改动

导致sensorservice无法正常启动，通过将正常包/system/framework中相关的 jar包逐个push到无法启动的机器发现：framework.jar push进去之后，sensorservice能够正常启动. 故，肯定是framework相关改动导致. 通过查看changes

发现正是包含上述案例中源码的change导致. 因为此时telephony.registry服务尚未注册到servicemanager中去，就开始通过ServiceManager的getService接口来获取telephony.registry的代理对象.

2. 问题：为什么上述案例中的代码会导致sensorservice无法正常add到servicemanager中呢？
A.查看servicemanager的log
打开servicemanager中相关log,发现add service的binder请求根本就没有发送到servicemanager中.

那么可以肯定一点：system_server  — add sensorservice request --> servicemanager 这个过程中出了问题.

B. 查看内核相关的log：

```
<5>[   22.727416] C3 [  system_server, 1150] type=1400 audit(1415277389.349:10): avc:  denied  { transfer } for  pid=1150 comm="system_server" scontext=u:r:zygote:s0 tcontext=u:r:servicemanager:s0 tclass=binder
```

通过查看kmsg可知，这是selinux导致的问题，于是我关闭selinux开关发现sensorservice能够正确的add到servicemanager中去，那么可以肯定一点，确实是selinux导致的问题.

C. 寻找binder通讯过程中与selinux权限的验证接口:

```
源码：kernel/driver/staging/android/binder.c:
static void binder_transaction(struct binder_proc *proc,
                   struct binder_thread *thread,
                   struct binder_transaction_data *tr, int reply)
{

            ......
           // 请注意这里的proc->tsk变量，在后面我们会介绍其是如何进行初始化操作的.
            if (security_binder_transfer_binder(proc->tsk, target_proc->tsk)) {
                return_error = BR_FAILED_REPLY;
                goto err_binder_get_ref_for_node_failed;
            }

            ......
}
```

正是在security_binder_transfer_binder这个函数无法通过selinux验证导致binder通讯失败而无法add 到servicemanager中去导致.

3. 进一步分析：

A. 冰山一角:
system_server   ---binder(add sensorservice) --> servicemanager

正是在这个通讯过程中由于selinux验证无法通过导致sensorservice无法注册到servicemanager中去，通过kmsg可知system_server的scontext和tcontext分别是：

```
scontext: scontext=u:r:zygote:s0
tcontext: tcontext=u:r:servicemanager:s0  // system_server的tcontext同时也是servicemanager的scontext
```

通过ps -Z命令查看正常启动机器，system_server的scontext应该是： u:r:system:s0， 但在这里却是zygote进程的scontext, 好端端的system_server进程的scontext 怎么TMD就变成了zygote进程的
scontext ？而查看对应zygote进程的sepolicy配置文件发现其并没有与servicemanager进程进行binder通讯的权限,

现在可以肯定的是正是案例中的代码造成了现在这种局面。接下来我们反过来分析下案例中代码的执行时机来发现.

B. 穿越冰山一角：
案 例中的代码是在静态块中new 出多个TelephonyManager实例，在TelephonyManager的构造函数中通过ServiceManager的getService 接口来获取telephony.registry服务的代理对象，在Android中,

zygote进程在启动的时候有个preload的操作，在preload的执行过程会调用如下接口来预加载指定名称的类：

```
    public static Class<?> forName(String className) throws ClassNotFoundException {
        return forName(className, true, VMStack.getCallingClassLoader());
    }
```

Class的forName函数的第二个参数设置为true代表在预加载指定类之后会需要初始化对应的static模块和变量，而此时还在是在zygote进程中执行.

而在初始话静态变量的过程中如下调用过程：

```
sRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                               "telephony.registry"));
```

这 个调用过程首先会通过调用IServiceManager::defaultServiceManager函数通过 ProcessState::self()->getContextObject()来获取servicemanager的代理对象，然后通过 servicemanager的代理对象来获取telephony.registry服务的代理对象，

而在new ProcessState的过程中会执行如下代码：

```
sp<ProcessState> ProcessState::self()   // ProcessState是设计模式中的单例模式，每个进程只能拥有其一个实例
{
    Mutex::Autolock _l(gProcessMutex);
    if (gProcess != NULL) {
        return gProcess;
    }
    gProcess = new ProcessState;
    return gProcess;
}

......

static int open_driver()
{
    int fd = open("/dev/binder", O_RDWR);
    ......
    return fd;
}

ProcessState::ProcessState()
    : mDriverFD(open_driver())
    , mVMStart(MAP_FAILED)
    , mManagesContexts(false)
    , mBinderContextCheckFunc(NULL)
    , mBinderContextUserData(NULL)
    , mThreadPoolStarted(false)
    , mThreadPoolSeq(1)
{
  ......
}
```

在new一个ProcessState的过程中会调用open系统调用来打开binder文件，这最终会陷入内核中执行如下代码：

```
static int binder_open(struct inode *nodp, struct file *filp)
{
    struct binder_proc *proc;
    proc = kzalloc(sizeof(*proc), GFP_KERNEL);
    if (proc == NULL)
        return -ENOMEM;
    get_task_struct(current);

    // 在这里将zygote进程的进程描述符保存到了binder_proc结构体的成员变量tsk中去.
    proc->tsk = current;
    ......
    // 又将proc结构保存到/dev/binder文件的结构struct file中的private_data成员变量中去.

    filp->private_data = proc;
   ......
}
```

ProcessState 是一个单例，也就是每个进程只能拥有ProcessState的一个实例，即：只要多个进程打开的/dev/binder的文件句柄值fd是一样的，代表 最终指向的binder_proc也是同一个, 即proc的成员变量tsk指向的也是同一个进程描述符.

C. 探明冰山下的靠山：
system_server 进程是调用IServiceManager::defaultService()函数中的 ProcessState()::self()->getContextObject()来获取servicemanager的代理对象，然后调用 addService来注册sensorservice到servicemanager中去的.

而案例中的代码同样是在zygote进程中 调用IServiceManager::defaultService()函数中的 ProcessState()::self()->getContextObject()来获取servicemanager的代理对象，然后调用 getService来获取telephony.registry服务的.

system_server进程是由zygote进程 fork()出来的，那么system_server进程和zygote进程将拥有完全相同的ProcessState()实例，完全相同的/dev /binder文件句柄值，完全相同的binder_proc变量，最终binder_proc指向的tsk也是同一个进程 --> zygote,

但是, fork系统调用在zygote进程中fork 出system_server进程的时候，两个进程对应selinux的scontext却是完全不一样的，最终就造成了system_server进程 向servicemanager中注册sensorservice的时候取出的binder_proc中的tsk是zygote进程，

在binder通讯的过程中就使用了zygote进程的scontext来进行selinux校验，最终导致了校验失败. 造成系统无法正常启动.

补充：在调查的过程中发现mediaserver进程在启动的过程中也会调用IServiceManager::defaultService()函数中的 ProcessState()::self()->getContextObject()然后调用addService来注册 listen.service到servicemanager中去，但是其是能够成功的，

这是因为mediaserver进程和zygote进程同属于init进程的子进程，其和zygote进程没有这种fork()关系. 不会共享ProcessState实例中的fd.

三. 如何避免这种问题

1.不要在static块或变量初始化过程中调用ServiceManager提供的API(addService, getService, checkService, listServices)
2. 在调用getService和checkService的时候一定要注意调用时机，确保对应的Server端已经注册到servicemanager中去，否则获取到的代理对象也是null, 调用没有任何意义.