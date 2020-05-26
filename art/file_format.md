# Apk, Dex, Oat, Vdex

现在市面上的 Android 手机大部分都是运行的是ART虚拟机了。还记得自己一部 Android手机（HuaweiG520），Android4.1 系统。那时候还是没有 ART虚拟机 的。作为Android开发者，我们应该对 Android 的发展历史有些了解为什么 Android 会经历这么多的变化。Android 是先有 JVM 然后是 Dalvik ，接着是现在的 ART虚拟机 。那么他们之间有什么关系呢？

## Dalvik和ART

Dalvik 是 Google 公司自己设计用于 Android 平台的虚拟机，是 Google 等厂商合作开发的 Android 移动设备平台的核心组成部分之一。它可以支持已转换为 .dex 格式的 Java 应用程序的运行，.dex 格式是专为Dalvik 设计的一种压缩格式，适合内存和处理器速度有限的系统。

### Dalvik和JVM的主要区别

首先通过介绍 Dalvik 的时候我们就知道 Dalvik 运行的是 dex 文件，而JVM 运行的是 class 文件。

Dalvik VM 是基于寄存器的架构，而 JVM 是栈机。所以 Dalvik VM 的好处是可以做到更好的提前优化（ahead-of-time optimization）。 另外基于寄存器架构的VM执行起来更快，但是代价是更大的代码长度。

基于寄存器架构的虚拟机有这么多的好处，为什么之前设计JAVA的程序员没有采用呢，而是采用现在基于栈的架构开发的呢？

因为基于栈的虚拟机也有它的优点，它不对 host 平台的 寄存器数量 做假设，有利于移植到不懂的平台，这也符合的Java跨平台的特点。而Dalvik 虚拟机则不关心这些，因为它本来就是为 ARM 这样的多寄存器平台设计的，另外 Dalvik 被移植到 x86 机器上，即使 x86 这种寄存器少的平台，寄存器架构的虚拟机也可以运行。

一般来说,基于堆栈的机器必须使用指令才能从堆栈上的加载和操作数据,因此,相对基于寄存器的机器，它们需要更多的指令才能实现相同的性能。但是基于寄存器机器上的指令必须经过编码,因此,它们的指令往往更大。

想要了解更多:基于栈的虚拟机 VS 基于寄存器的虚拟机

```
Dalvik在JVM上的优化
在编译时提前优化代码而不是等到运行时
虚拟机很小，使用的空间也小；被设计来满足可高效运行多种虚拟机实例。
常量池已被修改为只使用32位的索引，以简化解释器
标准Java字节码实行8位堆栈指令,Dalvik使用16位指令集直接作用于局部变量。局部变量通常来自4位的“虚拟寄存器”区。这样减少了Dalvik的指令计数，提高了翻译速度。
```

### Dalivk进化之ART

2014年6月25日，Android L 正式亮相于召开的谷歌I/O大会，Android L 改动幅度较大，谷歌将直接删除 Dalvik ，代替它的是传闻已久的 ART。在在Android系统4.4 提出，在 Android5.0之后完全弃用 dalvik 全部采用 art为执行环境。

#### ART（ Android Runtime）

ART 的机制与 Dalvik 不同。在 Dalvik 下，应用每次运行的时候，字节码都需要通过即时编译器（just in time ，JIT）转换为机器码，这会拖慢应用的运行效率，而在ART 环境中，应用在第一次安装的时候，字节码就会预先编译成机器码，使其成为真正的本地应用。这个过程叫做预编译（AOT,Ahead-Of-Time）。这样的话，应用的启动(首次)和执行都会变得更加快速。

ART的优缺点

优点:

```
系统性能的显著提升。
应用启动更快、运行更快、体验更流畅、触感反馈更及时。
更长的电池续航能力。
支持更低的硬件。
```

缺点：

```
机器码占用的存储空间更大，字节码变为机器码之后，可能会增加10%-20（不过在应用包中，可执行的代码常常只是一部分。比如最新的 Google+ APK 是 28.3 MB，但是代码只有 6.9 MB。）
应用的安装时间会变长。
class、dex、odex、ELF相爱相杀
从执行文件上面进行分析的话，JVM 对应 class 文件，Dalivk 对应 odex 文件，而 ART 对应 oat 文件。
```

工具：javac, dx

```
.java——>.class——->.dex
.java 文件经过 javac 编译器生成 .class 字节码 再经过。dx 工具生成 .dex 。
```

为了在 JVM 优化出一个 Dalivk 虚拟机，所以把 JVM 运行的 class 文件进行打包优化为 dex 文件，但其本质还是和 class 文件一样属于字节码文件。但是为了每次启动时都去掉从字节码到机器码的编译过程，Google 又从 Dalivk 中优化出了 ART，在其安装应用的时候将 dex 文件进行预处理生成可执行的 oat 文件。

#### JIT的引入

据说 Android 2.2 的虚拟机 dalvik 使用了 JIT 技术，使其运行速度快了5倍。

dalvik 解释并执行程序，JIT 技术主要是对多次运行的代码进行编译，当再次调用时使用编译之后的机器码，而不是每次都解释，以节约时间。5倍是测试程序测出的值，并不是说程序运行速度也能达到5倍，这是因为测试程序有很多的重复调用和循环，而一般程序主要是顺序执行的，而且它是一边运行，一边编译，一开始的时候提速不多，所以真正运行程序速度提高不是特别明显。

每启动一个应用程序，都会相应地启动一个 dalvik 虚拟机，启动时会建立JIT 线程，一直在后台运行。当某段代码被调用时，虚拟机会判断它是否需要编译成机器码，如果需要，就做一个标记，JIT 线程不断判断此标记，如果发现被设定就把它编译成机器码，并将其机器码地址及相关信息放入 entry table 中，下次执行到此就跳到机器码段执行，而不再解释执行，从而提高速度。

#### odex(optimized dex)

因为 apk 实际为 zip 压缩包，虚拟机每次加载都需要从 apk 中读取classes.dex 文件，这样会耗费很多的时间，而如果采用了 odex 方式优化的 dex 文件，他包含了加载 dex 必须的依赖库文件列表，只需要直接加载而不需要再去解析。
在 Android N 之前，对于在 dalvik 环境中 使用 dexopt 来对 dex 字节码进行优化生成 odex 文件最终存在手机的 data/dalvik-cache 目录下，最后把 apk 文件中的 dex 文件删除。
在 Android O 之后，odex 是从 vdex 这个文件中 提取了部分模块生成的一个新的可执行二进制码文件 ， odex 从 vdex 中提取后，vdex 的大小就减少了。

第一次开机就会生成在 /system/app/<packagename>/oat/ 下
在系统运行过程中，虚拟机将其 从 /system/app 下 copy 到 /data/davilk-cache/ 下;
odex + vdex = apk 的全部源码 （vdex 并不是独立于 odex 的文件 odex + vdex 才代表一个 apk ）;

#### AOT(Ahead-of-time)

ART 推出了预先 (AOT) 编译，可提高应用的性能。ART 还具有比 Dalvik 更严格的安装时验证。在安装时，ART 使用设备自带的 dex2oat 工具来编译应用。该实用工具接受 DEX 文件作为输入，并针对目标设备生成已编译应用的可执行文件。之后打开 App 的时候，不需要额外的翻译工作，直接使用本地机器码运行，因此运行速度提高。

AOT 是 art 的核心，oat 文件包含 oatdata 和 oatexec。前者包含 dex 文件内容，后者包含生成的本地机器指令，从这里看出 oat 文件回会比 dex 文件占用更大的存储空间。

因为 oat 文件包含生成的本地机器指令进而可以直接运行，它同样保存在手机的 data/dalvik-cache 目录下 PMS(PackgetManagerService)—>installd(守护进程)——>dex2oat(/system/bin/dex2oat) 。注意存放在 data/dalvik-cache 目录下的后缀名都仍为 .dex 前者其实表示一个优化过的 .dex 文件 后者为 .art 文件。

push 一个新的 apk 文件覆盖之前 /system/app 下 apk 文件，会触发 PKMS 扫描时下发 force_dex flag ，强行生成新的 vdex文件 ，覆盖之前的vdex 文件，由于某种机制，这个新 vdex 文件会 copy 到 /data/dalvik-cache/ 下，于是 art 文件也变化了。

#### 混合运行时

Android N 开发者预览版包含了一个混合模式的运行时。应用在安装时不做编译，而是解释字节码，所以可以快速启动。ART中有一种新的、更快的解释器，通过一种新的 JIT 完成，但是这种 JIT 的信息不是持久化的。取而代之的是，代码在执行期间被分析，分析结果保存起来。然后，当设备空转和充电的时候，ART 会执行针对“热代码”进行的基于分析的编译，其他代码不做编译。为了得到更优的代码，ART 采用了几种技巧包括深度内联。

对同一个应用可以编译数次，或者找到变“热”的代码路径或者对已经编译的代码进行新的优化，这取决于分析器在随后的执行中的分析数据。这个步骤仍被简称为 AOT，可以理解为“全时段的编译”（All-Of-the-Time compilation）。

这种混合使用 AOT、解释、JIT 的策略的全部优点如下。

即使是大应用，安装时间也能缩短到几秒;
系统升级能更快地安装，因为不再需要优化这一步;
应用的内存占用更小，有些情况下可以降低 50%;
改善了性能;
更低的电池消耗;

##### vdex

官网回答：ART的运作方式

dex2oat 工具接受一个 APK 文件，并生成一个或多个编译工件文件，然后运行时将会加载这些文件。文件的个数、扩展名和名称会因版本而异。
在 Android O 版本中，将会生成以下文件：

* .vdex：其中包含 APK 的未压缩 DEX 代码，另外还有一些旨在加快验证速度的元数据。
* .odex：其中包含 APK 中已经过 AOT 编译的方法代码。
* .art (optional)：其中包含 APK 中列出的某些字符串和类的 ART 内部表示，用于加快应用启动速度。

第一次开机就会生成在 /system/app/<packagename>/oat/ 下；

在系统运行过程中，虚拟机将其 从 /system/app 下 copy 到 /data/davilk-cache/ 下。

##### ELF文件

ELF(Executable and Linking Format)是一种对象文件的格式，用于定义不同类型的对象文件(Object files)中都放了什么东西、以及都以什么样的格式去放这些东西。它自最早在 System V系统上出现后，被 xNIX 世界所广泛接受，作为缺省的二进制文件格式来使用。可以说，ELF 是构成众多 xNIX 系统的基础之一。

apk安装过程
大家都知道 apk 其实就是 zip 包 apk 安装过程其实就是解压过程。
用户应用安装涉及以下几个目录：

data/app 安装目录 安装时会把 apk 文件 copy 到这里;
data/dalvik-cache 如上述描述中的存放.dex ( .odex 无论 davilk 的 dex 还是 art 的 oat 格式);
data/data/pkg/ 存放应用程序的数据;
Android5.1 版本下 oat 文件都以 .dex 文件在 data/dalvik-cache 目录下：

Android8.0 版本下 dex2oat 工具生成的三个.art，.odex，.vdex文件都在 data/dalvik-cache 目录下：
