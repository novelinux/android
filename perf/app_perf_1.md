## Perf

Original: https://blog.csdn.net/huangxiaoguo1/article/details/80434456

### 性能问题分类

* UI渲染问题：过度绘制、布局冗杂
* 内存问题：内存浪费（内存管理）、内存泄漏
* 功耗问题：耗电

### 性能优化原则和方法

#### 性能优化原则

坚持性能测试（开发和测试同学的测试方法略有不同）：不要凭感觉去检测性能问题、评估性能优化的效果，应该保持足够多的测量，用数据说话（主要针对测试同学）。使用各种性能工具测试及快速定位问题（主要针对开发同学）。

使用低配置的设备：同样的程序，在低端配置的设备中，相同的问题会暴露得更为明显。
权衡利弊：在能够保证产品稳定、按时完成需求的前提下去做优化。

#### 优化方法

* 了解问题（分为可感知和不可感知的性能问题）：对于性能问题来讲，这个步骤只适用于某些明显的性能问题，很多无法感知的性能问题需要通过工具定位。例如：内存泄漏、层级冗杂、过度绘制等无法感知。滑动卡顿是可以感知到的。
* 定位问题：通过工具检测、分析数据，定位在什么地方存在性能问题。
* 分析问题：找到问题后，分析针对这个问题该如何解决，确定解决方案。
* 解决问题：根据分析结果寻找解决方案。
* 验证问题：保证优化有效，没有产生新的问题，以及产品稳定性。

### 性能优化工具

### 性能优化指标

#### 渲染

* 滑动流畅度：FPS，即Frame per Second，一秒内的刷新帧数，越接近60帧越好；
* 过度绘制：单页面的3X（粉红色区域） Overdraw小于25%
* 启动时间：这里主要说的是Activity界面启动时间，一般低于300ms，需要用高频摄像机计算时间。

#### 内存

内存大小：峰值越低越好，需要优化前后做对比
内存泄漏：需要用工具检查对比优化前后

#### 功耗

单位时间内的掉电量，掉电量越少越好，业内没有固定标准。华为有专门测试功耗的机器，以及自己的标准

### 渲染问题

先来看看造成应用UI卡顿的常见原因都有哪些？

* 人为在UI线程中做轻微耗时操作，导致UI线程卡顿；
* 布局Layout过于复杂，无法在16ms内完成渲染；
* 同一时间动画执行的次数过多，导致CPU或GPU负载过重；
* View过度绘制，导致某些像素在同一帧时间内被绘制多次，从而使CPU或GPU负载过重；
* View频繁的触发measure、layout，导致measure、layout累计耗时过多及整个View频繁的重新渲染；
* 内存频繁触发GC过多（同一帧中频繁创建内存），导致暂时阻塞渲染操作；
* 冗余资源及逻辑等导致加载和执行缓慢；
* 臭名昭著的ANR；

[App ANR](../anr/README.md)

**大多数用户感知到的卡顿等性能问题的最主要根源都是因为渲染性能。（Google官方说的）**

Android系统每隔16ms发出VSYNC信号（vertical synchronization –场扫描同步，场同步，垂直同步），触发对UI进行渲染，如果每次渲染都成功，这样就能够达到流畅的画面所需要的60fps，为了能够实现60fps，这意味着程序的大多数操作都必须在16ms（1000/60=16.67ms）内完成。

如果你的某个操作花费时间是24ms，系统在得到VSYNC信号的时候就无法进行正常渲染，这样就发生了丢帧现象。那么用户在32ms内看到的会是同一帧画面。

#### 过度绘制

```
    Overdraw（过度绘制）描述的是屏幕上的某个像素在同一帧的时间内被绘制了多次。
    在多层次的UI结构里面，如果不可见的UI也在做绘制的操作，这就会导致某些像素区域被绘制了多次。
    这就浪费大量的CPU以及GPU资源，找出界面滑动不流畅、界面启动速度慢、手机发热。
```

如何查看过度绘制？

设置 — 开发中选项 — 调试GPU过度绘制

来看看里的过度绘制和优化效果（目前还存在很多待优化的页面）
[Overdraw](./overdraw.png)

上图中的各种颜色都代表什么意思？ [1x-4x](./1x-4x.png)

每个颜色的说明如下：

```
      原色：没有过度绘制
      紫色：1 次过度绘制
      绿色：2 次过度绘制
      粉色：3 次过度绘制
      红色：4 次及以上过度绘制
```

造成过度优化的关键是什么？多余的背景（Background）

* 布局中多余背景
* 代码里添加了多余的背景
* 弹窗底部布局不会导致弹窗本身过度绘制
* 自定义view时，通过Canvas的clipRect方法控制每个视图每次刷新的区域，这样可以避免刷新不必要的区域，从而规避过渡绘制的问题。还可以使用canvas.quickreject()来判断是否和某个矩形相交，从而跳过那些非矩形区域内的绘制操作。

http://jaeger.itscoder.com/android/2016/09/29/android-performance-overdraw.html

优化方法和步骤关键总结, 总结一下，优化步骤如下：

* 移除或修改Window默认的Background
* 移除XML布局文件中非必需的Background
* 按需显示占位背景图片
* 控制绘制区域

#### 布局优化

```
布局太过复杂，层级嵌套太深导致绘制操作耗时，且增加内存的消耗。
我们的目标就是，层级扁平化
```

布局优化的建议：

* 第一个建议：可以使用相对布局减少层级的就使用相对布局，否则使用线性布局。Android中RelativeLayout和LinearLayout性能分析，参考：http://www.jianshu.com/p/8a7d059da746#
* 第二个建议：用merge标签来合并布局，这可以减少布局层次。
* 第三个建议：用include标签来重用布局，抽取通用的布局可以让布局的逻辑更清晰明了，但要避免include乱用。
* 第四个建议：避免创建不必要的布局层级。（最容易发生的！）
* 第五个建议：使用惰性控件ViewStub实现布局动态加载.

```
这个标签最大的优点是当你需要时才会加载，使用他并不会影响UI初始化时的性能。通常情况下我们需要在某个条件下使用某个布局的时候会通过gone或者invisible来隐藏，其实这样的方式虽然隐藏了布局，但是当显示该界面的时候还是将该布局实例化的。使用ViewStub可以避免内存的浪费，加快渲染速度。
其实ViewStub就是一个宽高都为0的一个View，它默认是不可见的，只有通过调用setVisibility函数或者Inflate函数才会将其要装载的目标布局给加载出来，从而达到延迟加载的效果，这个要被加载的布局通过android:layout属性来设置。

```

如何借助工具查看代码布局？

```
Android SDK 工具箱中有一个叫做 Hierarchy Viewer 的工具，能够在程序运行时分析 Layout。
可以用这个工具找到 Layout 的性能瓶颈
该工具的使用条件：模拟器或者Root版真机。
如何开启该功能：AndroidStudio中，Tools — Android — Android Devices Monitor
该工具的缺点：使用起来麻烦。
```

#### 渲染性能的工具

* GPU Render模式分析（大致定位问题）

http://www.voidcn.com/article/p-ofvsxbuh-bch.html

```
开发者选项 — GPU呈现模式分析 — 选择“在屏幕上显示为条形图”
```

* GPU Monitor

* 启用严格模式（不止渲染性能）

http://www.tuicool.com/articles/ueeM7b6

```
应用在主线程上执行长时间操作时会闪烁屏幕。
```

### 内存问题

#### 内存浪费

程序内存的管理是否合理高效对应用的性能有着很大的影响。

推荐阅读Android性能优化典范-第3季，参考：http://hukai.me/android-performance-patterns-seaso
ArrayMap Android源码中很多使用, Android为移动操作系统特意编写了一些更加高效的容器，例如ArrayMap、SparseArray。为了解决HashMap更占内存的弊端，Android提供了内存效率更高的ArrayMap。

先来看看HashMap的原理, HashMap的整体结构如下：

[HashMap](./hashmap.png)

存储位置的确定流程：

[HashMap Find](./hashmap-find.png)

再看来看看ArrayMap是如何优化内存的: 它内部使用两个数组进行工作，其中一个数组记录key hash过后的顺序列表，另外一个数组按key的顺序记录Key-Value值，如下图所示：

[ArrayMap](./ArrayMap.jpeg)

当你想获取某个value的时候，ArrayMap会计算输入key转换过后的hash值，然后对hash数组使用二分查找法寻找到对应的index，然后我们可以通过这个index在另外一个数组中直接访问到需要的键值对。

[ArrayMap Find](./arraymap-find.png)

什么时候使用ArrayMap呢？

```
1.对象个数的数量级最好是千以内，没有频繁的插入删除操作
2.数据组织形式包含Map结构
```

* Autoboxing（避免自动装箱）

```
Autoboxing的行为还经常发生在类似HashMap这样的容器里面，对HashMap的增删改查操作都会发生了大量的autoboxing的行为。当key是int类型的时候，HashMap和ArrayMap都有Autoboxing行为。
```

* SparseArray（

```
为了避免Autoboxing行为Android提供了SparseArray，此容器使用于key为int类型。SparseBooleanMap，SparseIntMap，SparseLongMap等容器，是key为int，value类型相应为boolean、int、long等。
```

* Enum（枚举，项目中较多使用，应尽量避免）

```
Enums often require more than twice as much memory as static constants. You should strictly avoid using enums on Android.
```

Android官方强烈建议不要在Android程序里面使用到enum。

关于enum的效率，请看下面的讨论。假设我们有这样一份代码，编译之后的dex大小是2556 bytes，
在此基础之上，添加一些如下代码，这些代码使用普通static常量相关作为判断值：

```
增加上面那段代码之后，编译成dex的大小是2680 bytes，相比起之前的2556 bytes只增加124 bytes。假如换做使用enum，使用enum之后的dex大小是4188 bytes，相比起2556增加了1632 bytes，增长量是使用static int的13倍。不仅仅如此，使用enum，运行时还会产生额外的内存占用.
```

#### 内存泄漏

[Memory Leak](./leaks/Android.md)

### 性能优化必备神器推荐

#### Lint