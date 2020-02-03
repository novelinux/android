## ART

### ART 的运作方式

ART 使用预先 (AOT) 编译，并且从 Android 7.0（代号 Nougat，简称 N）开始结合使用 AOT、即时 (JIT) 编译和配置文件引导型编译。所有这些编译模式的组合均可配置，我们将在本部分中对此进行介绍。例如，Pixel 设备配置了以下编译流程：

最初安装应用时不进行任何 AOT 编译。应用前几次运行时，系统会对其进行解译，并对经常执行的方法进行 JIT 编译。
当设备闲置和充电时，编译守护进程会运行，以便根据在应用前几次运行期间生成的配置文件对常用代码进行 AOT 编译。

下一次重新启动应用时将会使用配置文件引导型代码，并避免在运行时对已经过编译的方法进行 JIT 编译。在应用后续运行期间进行了 JIT 编译的方法将会被添加到配置文件中，然后编译守护进程将会对这些方法进行 AOT 编译。
ART 包括一个编译器（dex2oat 工具）和一个为启动 Zygote 而加载的运行时 (libart.so)。dex2oat 工具接受一个 APK 文件，并生成一个或多个编译工件文件，然后运行时将会加载这些文件。文件的个数、扩展名和名称会因版本而异，但在 Android O 版本中，将会生成以下文件：

* .vdex：其中包含 APK 的未压缩 DEX 代码，另外还有一些旨在加快验证速度的元数据。
* .odex：其中包含 APK 中已经过 AOT 编译的方法代码。
* .art (optional)：其中包含 APK 中列出的某些字符串和类的 ART 内部表示，用于加快应用启动速度。

### dexopt

[dexopt](./dexopt.png)

### dex2oat

ART 的编译选项分为以下两个类别：

系统 ROM 配置：编译系统映像时，会对哪些代码进行 AOT 编译。
运行时配置：ART 如何在设备上编译和运行应用。
用于配置这两个类别的一个核心 ART 选项是“编译过滤器”。编译过滤器可控制 ART 如何编译 DEX 代码，是一个传递给 dex2oat 工具的选项。从 Android O 开始，有四个官方支持的过滤器：

* verify： 只运行 DEX 代码验证。
* quicken： 运行 DEX 代码验证，并优化一些 DEX 指令，以获得更好的解译器性能。
* speed： 运行 DEX 代码验证，并对所有方法进行 AOT 编译。
* speed-profile： 运行 DEX 代码验证，并对配置文件中列出的方法进行 AOT 编译。

verify和quicken都没执行编译，之后代码跑解释器．而spped-profile和speed都执行了编译，区别是speed-profile根据profile记录的热点函数来编译，属于部分编译，而speed属于全部编译．

执行效率：verify < quicken < speed-profile < speed

编译速度：verify > quicken > speed-profile > speed

[dex2oat log](./dex2oat.log)


### 系统 ROM 配置

有一些 ART 编译选项可用于配置系统 ROM。如何配置这些选项取决于 /system 的可用存储空间以及预先安装的应用数量。编译到系统 ROM 中的 JAR/APK 可以分为以下四个类别：

启动类路径代码：默认使用 speed 编译过滤器进行编译。
系统服务器代码：默认使用 speed 编译过滤器进行编译。
产品专属的核心应用：默认使用 speed 编译过滤器进行编译。
所有其他应用：默认使用 quicken 编译过滤器进行编译。

### ART编译流程

[ART编译流程](./art.webp)

* 获取APK，通过dex2oat⼯具，按编译过滤规则来处理APK, 对应三⽅应⽤来说，会在data/app/应⽤ 包名+后续⼀串乱码/oat/arm下⽣成.vdex 、.odex、.art⽂件。
* 解析.odex，加载⽬标类进内存。
* 执⾏⽅法，先看当前⽅法是否被编译过，如果被编译过，优先使⽤JIT编译出来的机器码。如果没被编译过，⾛解释器执⾏，通过profile⽂件记录执⾏信息，超过⼀定执⾏次数之后，当前⽅法会变为 hot code, 该⽅法会通过JIT thread pool起⼦线程⾛JIT编译，⽣成机器码缓存在内存。 
*  JIT编译过程会判断是否有⾜够内存，如果没有会触发回收。 
* 在应⽤安装，动态加载， 系统升级， 后台优化( BackgroundDexoptService)等场景下，会触发 dex2oat按filter来编译。

```shell
dumpsys package packagename
adb shell cmd package compile -c -f -m speed packagename
```

```shell
adb shell am start -n com.ss.android.ugc.aweme/.main.MainActivity
```
