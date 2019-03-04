# 在Activity中通过setResult和finish来处理Intent数据的陷阱

之前调查在红米上批量删除联系人手机黑屏，联系人无法删除, ps查看发现多个android.process.acore进程

1. 首先从LOG中看到这样一段异常：

01-15 19:17:29.052   511   511 E JavaBinder: !!! FAILED BINDER TRANSACTION !!!
01-15 19:17:29.052   511   511 I ActivityManager: Restarting because process died: ActivityRecord{431a0db0 u0 com.android.contacts/.activities.BatchProcessActivity}
01-15 19:17:29.052   511   511 V ActivityManager: ACT-Launching: ActivityRecord{431a0db0 u0 com.android.contacts/.activities.BatchProcessActivity}
01-15 19:17:29.053   511   511 V Provider/Settings:  from settings cache , name = keep_launcher_in_memory , value = null

01-15 19:17:29.058   511   511 E JavaBinder: !!! FAILED BINDER TRANSACTION !!!
01-15 19:17:29.060   511   511 W ActivityManager: Exception when starting activity com.android.contacts/.activities.BatchProcessActivity
01-15 19:17:29.060   511   511 W ActivityManager: android.os.TransactionTooLargeException
01-15 19:17:29.060   511   511 W ActivityManager:     at android.os.BinderProxy.transact(Native Method)
01-15 19:17:29.060   511   511 W ActivityManager:     at android.app.ApplicationThreadProxy.scheduleLaunchActivity(ApplicationThreadNative.java:722)
01-15 19:17:29.060   511   511 W ActivityManager:     at com.android.server.am.ActivityStack.realStartActivityLocked(ActivityStack.java:787)
01-15 19:17:29.060   511   511 W ActivityManager:     at com.android.server.am.ActivityStack.startSpecificActivityLocked(ActivityStack.java:895)
01-15 19:17:29.060   511   511 W ActivityManager:     at com.android.server.am.ActivityStack.resumeTopActivityLocked(ActivityStack.java:1934)
01-15 19:17:29.060   511   511 W ActivityManager:     at com.android.server.am.ActivityStack.resumeTopActivityLocked(ActivityStack.java:1543)
01-15 19:17:29.060   511   511 W ActivityManager:     at com.android.server.am.ActivityStack.completePauseLocked(ActivityStack.java:1283)
01-15 19:17:29.060   511   511 W ActivityManager:     at com.android.server.am.ActivityStack.activityPaused(ActivityStack.java:1164)
01-15 19:17:29.060   511   511 W ActivityManager:     at com.android.server.am.ActivityManagerService.activityPaused(ActivityManagerService.java:5125)

查看到511是system_server进程，从LOG大致可以看出是Server端将一个处于Paused状态的Activity(BatchProcessActivity)重新resume, 结果发生了异常。


整个异常是发生在Server端通过Binder向Client端传送数据时由于数据量过大发生的. 究竟Server端有多大的数据需要transact给Client进行处理才会发生这个异常，从这个LOG是看不出来了。

2. 接下来先看调查为什么要resume BatchProcessActivity ?

Client端在点击删除按钮的时候触发了什么动作。
最终定位到执行批量删除的动作是在ContactPhonePickerActivity的onClick中执行的,大致的调用流程如下：

onClick --> onOk --> getCheckedContactArray()              //  getCheckedContactArray是将所有选中的联系人的uri放到一个String的Array中去.


                              --> returnMultiContactPickerResult     //  在这个方法中，将前面保存联系人uri的Array保存到Intent中去， 关键字是ExtraIntent.EXTRA_PICKED_MULTIPLE_CONTACTS


                              --> setResult  --> finish                         //  接着调用了setResult方法和finish方法，让对应的Target来进行处理

接着通过搜索EXTRA_PICKED_MULTIPLE_CONTACTS发现对应的联系人数据是要在BatchProcessActivity的onActivityResult方法中去处理的，从上面的调用流程看并没有发现启动
BatchProcessActivity的调用, 最终发现是在ContactsMorePreferencesFragment的onPreferenceClick中点击"批量删除联系人"这个Preference的时候就已经启动了，但是处于Pause状态
而等到选中联系人删除的时候，又需要将BatchProcessActivity从pause状态resume。如下LOG:

01-15 19:17:13.707   511   828 I ActivityManager: START u0 {act=action_batch_delete cmp=com.android.contacts/.activities.BatchProcessActivity} from pid 4009

4009 是android.process.acore进程， BatchProcessActivity是在android.process.acore进程中启动的。


3. 接下来看，为什么在system_server中会发生TransactionTooLargeException?

调试发现，在调用ActivityStack.resumeTopActivityLocked -----> ActivityStack.startSpecificActivityLocked, 在这个调用过程中:

Server(system_server)端需要将刚才从Client端(android.process.acore)发送过来的联系人uri数据
在resume BatchProcessActivity进行处理时需要调用ApplicationThreadProxy.scheduleSendResult发送给client端进行处理，这时候由于数据量过大而发生异常。

可是为什么从Client发送同样大小的数据给Server端时没有发生这个Exception而在Server端将数据回传给Client端的时候发生异常了呢？
查看kmsg发现如下LOG:

<3>[ 2361.816649] (0)[511:system_server]binder: 4009: binder_alloc_buf size 1275892 failed, no async space left (1044480)
<6>[ 2361.816673] (0)[511:system_server]binder: buffer allocation failed on 4009:0 async from -1:0 size 1275892
<3>[ 2361.816684] (0)[511:system_server]binder: 511:511 buffer allocation failed on 4009:0
<6>[ 2361.816695] (0)[511:system_server]binder: 511:511 transaction failed 29201, size 1275888-4

其中 4009 是target_proc, 也就是目标进程，传送的数据大小是1275892个字节， 从no async space可以看出这是个异步通信，异步通信支持的数据量大小是1044480个字节。1275892 > 1044480 ，导致在目标进程的内核空间中分配
物理内存的时候（这时候也许物理内存已经分配好了，只需要从对应的空闲内存的红黑树中找一块足够大小的就可以了），但是这时候尚未查找或分配的时候进行 check就失败了，进行一系列的调用返回最终就在framework层抛出了TransactionTooLargeException.

接下来查看Binder允许每个进程进行通信的数据量大小，在ProcessState的构造函数可以查看到：

mVMStart = mmap(0, BINDER_VM_SIZE, PROT_READ, MAP_PRIVATE | MAP_NORESERVE, mDriverFD, 0);

BINDER_VM_SIZE = (2MB - 8KB), 查看对应的binder驱动发现，这个大小是Binder允许最大的同步通信的数据量大小，而异步通信只是同步通信的数据量大小的一半，1044480 个字节正好等于(2MB - 8KB) / 2.

注意：在原生Android上BINDER_VM_SIZE的大小仅为(1MB - 8KB), 而MTK的是2MB - 8KB，设置相当的奇葩.

而联系人uri的数据量大小是1275892个Byte正好处于Binder允许的同步和异步通信数据量之间。

再次返回去查看，从Client到Server端传送数据使用的是同步通信， 从Server端到Client端的通信是异步通信，这就解释了为什么从Client发送同样大小的数据给Server端时没有发生这个 Exception而在Server端将数据回传给Client端的时候发生了。


4. 解释了发生上述异常的原因，接下来还有一个问题困惑着我们，发生了这个Exception为什么会有多个android.process.acore进程被fork出来?

现在来看这个调用过程：--> ActivityStack.resumeTopActivityLocked -----> ActivityStack.startSpecificActivityLocked， 是由于发生了TransactionTooLargeException之后才调用的startSpecificActivityLocked方法, 具体流程如下：


ActivityStack.resumeTopActivityLocked  --> ApplicationThreadProxy.scheduleSendResult(发生了 TransactionTooLargeException，在catch这个Exception之后) --> ActivityStack.startSpecificActivityLocked

--> ActivityStack.realStartActivityLocked --> ApplicationThreadProxy.scheduleLaunchActivity(BatchProcessActivity)依旧是由于 发生Exception，在catch这个Exception之后调用

--> ActivityManagerService.startProcessLocked  --> ActivityManagerService.handleAppDiedLocked(在这个函数中会把进程Map中的进程 android.process.acore从中删除，但是并没有释放进程占用的资源)

 --> ActivityStack.resumeTopActivityLocked  --> 就这样又回到了开头形成了一个环，就造成不断的fork android.process.acore进程。



5. 总结：

从上述分析看，这其实是原生Android系统遗留的一个bug, 但是对于App开发的同学们要在做如下调用的时候要小心了:

Activity1 调用： setResult  --> finish 调用了setResult方法和finish方法来传递Intent数据让对应的Activity2调用onActivityResult方法来进行处理

那么一定要注意在Activity1中设置的Intent大小，一定要考虑Intent的大小是否会处于如下大小范围:

(BINDER_VM_SIZE) / 2  < Intent的Size < BINDER_VM_SIZE

如果Intent的大小有可能处于这个大小范围那么将会出现如上分析的问题.

