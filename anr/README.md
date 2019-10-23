## ANR

首先，ANR(Application Not responding)是指应用程序未响应，Android系统对于一些事件需要在一定的时间范围内完成，如果超过预定时间能未能得到有效响应或者响应时间过长，都会造成ANR。ANR由消息处理机制保证，Android在系统层实现了一套精密的机制来发现ANR，核心原理是消息调度和超时处理。

其次，ANR机制主体实现在系统层。所有与ANR相关的消息，都会经过系统进程(system_server)调度，然后派发到应用进程完成对消息的实际处理，同时，系统进程设计了不同的超时限制来跟踪消息的处理。 一旦应用程序处理消息不当，超时限制就起作用了，它收集一些系统状态，譬如CPU/IO使用情况、进程函数调用栈，并且报告用户有进程无响应了(ANR对话框)。

然后，ANR问题本质是一个性能问题。ANR机制实际上对应用程序主线程的限制，要求主线程在限定的时间内处理完一些最常见的操作(启动服务、处理广播、处理输入)， 如果处理超时，则认为主线程已经失去了响应其他操作的能力。主线程中的耗时操作，譬如密集CPU运算、大量IO、复杂界面布局等，都会降低应用程序的响应能力。

### 哪些场景会造成ANR？

1. 发生ANR时会调用AppNotRespondingDialog.show()方法弹出对话框提示用户

2. AppErrors.appNotResponding()，该方法是最终弹出ANR对话框的唯一入口，调用该方法的场景才会有ANR提示，也可以认为在主线程中执行无论再耗时的任务，只要最终不调用该方法，都不会有ANR提示，也不会有ANR相关日志及报告；通过调用关系可以看出哪些场景会导致ANR，有以下四种场景：

*（1）Service Timeout:Service在特定的时间内无法处理完成;
*（2）BroadcastQueue Timeout：BroadcastReceiver在特定时间内无法处理完成;
*（3）ContentProvider Timeout：内容提供者执行超时;
*（4）inputDispatching Timeout: 按键或触摸事件在特定时间内无响应。

### ANR机制

可以分为两部分：

* ANR监测机制：Android对于不同的ANR类型(Broadcast, Service, InputEvent)都有一套监测机制。
* ANR报告机制：在监测到ANR以后，需要显示ANR对话框、输出日志(发生ANR时的进程函数调用栈、CPU使用情况等)。

整个ANR机制的代码也是横跨了Android的几个层：

* App层：应用主线程的处理逻辑；
* Framework层：ANR机制的核心，主要有AMS、BroadcastQueue、ActiveServices、InputmanagerService、InputMonitor、InputChannel、ProcessCpuTracker等；
* Native层：InputDispatcher.cpp；

Provider超时机制遇到的比较少，暂不做分析；Broadcast目前主要想说两个知识点：

第一：无论是普通广播还是有序广播，最终广播接受者的onreceive都是串行执行的，可以通过Demo进行验证；
第二：通过Demo以及框架添加相关日志，都验证了普通广播也会有ANR监测机制，ANR机制以及问题分析文章认为只有串行广播才有ANR监测机制，后续再会专门讲解Broadcast发送及接收流程，同时也会补充Broadcast ANR监测机制；本文主要以Servie处理超时、输入事件分发超时为例探讨ANR监测机制。

### Service超时监测机制

Service运行在应用程序的主线程，如果Service的执行时间超过20秒，则会引发ANR。

当发生Service ANR时，一般可以先排查一下在Service的生命周期函数中(onCreate(), onStartCommand()等)有没有做耗时的操作，譬如复杂的运算、IO操作等。 如果应用程序的代码逻辑查不出问题，就需要深入检查当前系统的状态：CPU的使用情况、系统服务的状态等，判断当时发生ANR进程是否受到系统运行异常的影响。

如何检测Service超时呢？Android是通过设置定时消息实现的。定时消息是由AMS的消息队列处理的(system_server的ActivityManager线程)。 AMS有Service运行的上下文信息，所以在AMS中设置一套超时检测机制也是合情合理的。我们先抛出两个问题：

* Service启动流程？
* 如何监测Service超时？

主要通过以上两个问题来说明Service监测机制，在知道Service启动流程之后，通过Service启动流程可以更容易分析Service超时监测机制。

1.Service启动流程如下图所示：

[Service Start](./service_start.png)

1).启动一个新的进程
2).从Intent中取得Service的相关信息(如要运行的Service的类名)
3).将新进程的pid与Service的相关信息保存到ActivityManagerService中

涉及实体: [Activity Class](./activity_class.png)

由图可知,当调用Activity的startService时,实际上调用的是ActivityManagerProxy,而从图中看出,IAcitityManager, ActivityManagerProxy, ActivityManagerService是典型的Binder机制结构, 所以最终调用的是ActivityManagerService.startService().

对于ActivityThread, 在Android系统中,每一个进程都用一个ActivityThread实例表示,每一个进程都与ActivityThread相关联.体现以上说法的是ActivityManagerService.startProcessLocked()中的如下代码:

```
int pid = Process.start("android.app.ActivityThread",  mSimpleProcessManagement ? app.processName : null, uid, uid, gids, debugFlags, null);
```

可见,ActivityThread是由新的进程执行,而其main函数就是新进程程序的入口.

ActivityThread保有一个ApplicationThread的对象,ApplicationThread是IApplicationThread的本地接口,那么远端接口在哪里呢? 远端接口在ActivityManagerService那里,是由第二阶段的ActivityManagerProxy.attachApplication(IApplicationThread)将IApplicationThread的远端接口发送给ActivityManagerService的.

由本地接口与远端接口所处的进程可以知道进程通信的方向是由ActivityManagerProxy到新的进程.当ActivityManagerService将其第二阶段中保存的Service信息传给新的进程之后,由新的进程负责创建Service的实例.在新进程中,会通过Looper消息机制来创建Service实例.而Looper消息机制的建立正是在第一阶段中ActivityThread.main()函数中建立的.创建完Service实例后,会调用其onCreate()方法.

由以上分析可知,上面的进程通信有两个方向,一个是进程到ActivityManagerService, 另一个方向是ActivityManagerService到进程. 于是就有两个Binder机制与之相对应.

对于进程到ActivityManagerService:

* ActivityManagerProxy
* ActivityManagerService
* IActivityManager

对于ActivityManagerService到进程:

* IApplicationThread
* ApplicationThreadProxy
* ApplicationThread

其实,IApplicationThread与IActivityManager都是对四大组件进行管理,而在其中扮演不同的角色罢了.

另外,ActivityThread也并非只和Activity相关,它与一个进程相关联,或者说它就代表了一个进程,与IActivityManager, IApplicationThread配合,对activity, broadcast,service等进行管理

所以在新的进程中启动Service或Activity的过程的关键点如下:

利用两套Binder机制信息交换
无论是Service还是Activity,都会执行ActivityThread.main方法, 从而在新的进程中可以处理UI的重绘.

2.Service超时监测机制

Service超时监测机制可以从Service启动流程中找到。

ActivityServices.realStartServiceLocked -> ActivityServices.bumpServiceExecutingLocked
-> ActivityServices.scheduleServiceTimeoutLocked

```
void scheduleServiceTimeoutLocked(ProcessRecord proc) {
4116        if (proc.executingServices.size() == 0 || proc.thread == null) {
4117            return;
4118        }
4119        Message msg = mAm.mHandler.obtainMessage(
4120                ActivityManagerService.SERVICE_TIMEOUT_MSG);
4121        msg.obj = proc;
            // 前台进程中执行Service，SERVICE_TIMEOUT=20s；
            // 后台进程中执行Service，SERVICE_BACKGROUND_TIMEOUT=200s
4122        mAm.mHandler.sendMessageDelayed(msg,
4123                proc.execServicesFg ? SERVICE_TIMEOUT : SERVICE_BACKGROUND_TIMEOUT);
4124    }
```

在onCreate生命周期开始执行前，启动超时监测，如果在指定的时间onCreate没有执行完毕（该该方法中执行耗时任务），就会调用ActiveServices.serviceTimeout()方法报告ANR；如果在指定的时间内onCreate执行完毕，那么就会调用ActivityManagerService.serviceDoneExecutingLocked()方法移除SERVICE_TIMEOUT_MSG消息，说明Service.onCreate方法没有发生ANR，Service是由AMS调度，利用Handler和Looper，设计了一个TIMEOUT消息交由AMS线程来处理，整个超时机制的实现都是在Java层；以上就是Service超时监测的整体流程。

### 输入事件超时监测

应用程序可以接收输入事件(按键、触屏、轨迹球等)，当5秒内没有处理完毕时，则会引发ANR。
这里先把问题抛出来了：输入事件经历了一些什么工序才能被派发到应用的界面？如何检测到输入时间处理超时？

1.Android输入系统简介Android输入系统总体流程与参与者如下图所示。

[Input Event](./input_event_timeout.jpeg)

单来说，内核将原始事件写入到设备节点中，InputReader在其线程循环中不断地从EventHub中抽取原始输入事件，进行加工处理后将加工所得的事件放入InputDispatcher的派发发队列中。InputDispatcher则在其线程循环中将派发队列中的事件取出，查找合适的窗口，将事件写入到窗口的事件接收管道中。窗口事件接收线程的Looper从管道中将事件取出，交由窗口事件处理函数进行事件响应。

关键流程有：
1.原始输入事件的读取与加工；
2.输入事件的派发；
3.输入事件的发送、接收与反馈。

其中输入事件派发是指InputDispatcher不断的从派发队列取出事件、寻找合适的窗口进行发送的过程，输入事件的发送是InputDispatcher通过Connection对象将事件发送给窗口的过程。
InputDispatcher与窗口之间的跨进程通信主要通过InputChannel来完成。在InputDispatcher与窗口通过InputChannel建立连接之后，就可以进行事件的发送、接收与反馈；

其中，将输入事件注入派发队列后，会唤醒派发线程，派发线程循环由InputDispatcher.dispatchOnce函数完成；InputDispatcher将事件以InputMessage写入InputChannel之后，窗口端的looper被唤醒，进而执行NativeInputReceiver::handleEvent()开始输入事件的接收，从InputEventReceiver开始输入事件被派发到用户界面；以上只是输入事件的大致流程，更详细的流程可以参考相关资料；

（1）InputDispatcher::dispatchOnceInnerLocked():根据事件类型选择不同事件的处理方法：InputDispatcher::dispatchKeyLocked()或者InputDispatcher::dispatchMotionLocked()，我们以按键事件超时监测为例进行说明；

（2）findFocusedWindowTargetsLocked()方法会调用checkWindowReadyForMoreInputLocked（）;该方法检查窗口是否有能力再接收新的输入事件；可能会有一系列的场景阻碍事件的继续派发，相关场景有：
* 场景1: 窗口处于paused状态，不能处理输入事件“Waiting because the [targetType] window is paused.”

* 场景2: 窗口还未向InputDispatcher注册，无法将事件派发到窗口“Waiting because the [targetType] window’s input channel is not registered with the input dispatcher. The window may be in the process of being removed.”

* 场景3: 窗口和InputDispatcher的连接已经中断，即InputChannel不能正常工作“Waiting because the [targetType] window’s input connection is [status]. The window may be in the process of being removed.”

* 场景4: InputChannel已经饱和，不能再处理新的事件“Waiting because the [targetType] window’s input channel is full. Outbound queue length: %d. Wait queue length: %d.”

* 场景5: 对于按键类型(KeyEvent)的输入事件，需要等待上一个事件处理完毕“Waiting to send key event because the [targetType] window has not finished processing all of the input events that were previously delivered to it. Outbound queue length: %d. Wait queue length: %d.”

* 场景6: 对于触摸类型(TouchEvent)的输入事件，可以立即派发到当前的窗口，因为TouchEvent都是发生在用户当前可见的窗口。但有一种情况， 如果当前应用由于队列有太多的输入事件等待派发，导致发生了ANR，那TouchEvent事件就需要排队等待派发。“Waiting to send non-key event because the %s window has not finished processing certain input events that were delivered to it over %0.1fms ago. Wait queue length: %d. Wait queue head age: %0.1fms.”

以上这些场景就是我们常在日志中看到的ANR原因的打印。

(3) 其中事件分发5s限制定义在InputDispatcher.cpp；InputDispatcher::handleTargetsNotReadyLocked（）方法中如果事件5s之内还没有分发完毕，则调用InputDispatcher::onANRLocked()提示用户应用发生ANR；

(4)当应用主线程被卡住的事件，再点击该应用其它组件也是无响应，因为事件派发是串行的，上一个事件不处理完毕，不会处理下一个事件。

(5)Activity.onCreate执行耗时操作，不管用户如何操作都不会发生ANR，因为输入事件相关监听机制还没有建立起来；InputChannel通道还没有建立这时是不会响应输入事件，InputDispatcher还不能事件发送到应用窗口，ANR监听机制也还没有建立，所以此时是不会报告ANR的。

（6）输入事件由InputDispatcher调度，待处理的输入事件都会进入队列中等待，设计了一个等待超时的判断，超时机制的实现在Native层。以上就是输入事件ANR监测机制；具体逻辑请参考相关源码；

### ANR报告机制

无论哪种类型的ANR发生以后，最终都会调用 AppErrors.appNotResponding() 方法，所谓“殊途同归”。这个方法的职能就是向用户或开发者报告ANR发生了。 最终的表现形式是：弹出一个对话框，告诉用户当前某个程序无响应;输入一大堆与ANR相关的日志，便于开发者解决问题。

除了主体逻辑，发生ANR时还会输出各种类别的日志：event log：通过检索”am_anr”关键字，可以找到发生ANR的应用main log：通过检索”ANR in “关键字，可以找到ANR的信息，日志的上下文会包含CPU的使用情况dropbox：通过检索”anr”类型，可以找到ANR的信息traces：发生ANR时，各进程的函数调用栈信息

至此ANR相关报告已经完成，后续需要分析ANR问题，分析ANR往往是从main log中的CPU使用情况和traces中的函数调用栈开始。所以，更新CPU的使用信息updateCpuStatsNow()方法和打印函数栈dumpStackTraces()方法，是系统报告ANR问题关键所在，具体分析ANR问题请参考相关资料。

### 总结

1. ANR的监测机制：首先分析Service和输入事件大致工作流程，然后从Service，InputEvent两种不同的ANR监测机制的源码实现开始，分析了Android如何发现各类ANR。在启动服务、输入事件分发时，植入超时检测，用于发现ANR。
2.ANR的报告机制：分析Android如何输出ANR日志。当ANR被发现后，两个很重要的日志输出是：CPU使用情况和进程的函数调用栈，这两类日志是我们解决ANR问题的利器。
3. 监测ANR的核心原理是消息调度和超时处理。
4. 只有被ANR监测的场景才会有ANR报告以及ANR提示框。