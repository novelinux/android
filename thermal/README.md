# thermal

包括两个部分kernel thermal monitor（KTM）和thermal engine。

Thermal 管理包括的内容如下：
* 硅片结温
* 内存温度限制
* 外表面温度限制

* 当thermal engine完全初始化后，KTM确保所有环境条件下的结温处于限定的范围之内。
* Thermal engine monitor监控系统的温度限制范围。
* 机械结构设计模拟是获得最佳性能的必要步骤
* Thermal management软件控制thermal响应。

## Android温度限制和特点

### KTM

* 内核启动时保护系统
* 设置110°为CPU热插拔的门限
* 将控制移交给thermal engine

#### KTM设置

example: msm8916.dtsi

CPU0传感器用于控制算法，如果温度超过limit-temp给定的值，CPU的最高主频将被限制，如果后续轮询温度继续升高，则频率会被进一步降低，轮询的时间间隔是poll-ms定义的值。如果温度调到limit-temp和temp-hysteresis之和以下，那么可以达到的最高主频将被增加。CPU频率的高低在DCVS表中仅一步。
除了CPU调频，第二个温度门限core-limit-temp定义了CPU热插拔的门限。当温度超过该门限时CPU将被unplug。

设备树中的freq-control-mask和core-control-mask定义了那个cpu核按上述定义的规则工作，bit 0对应CPU0，默认core-control-mask 不包括CPU0，因为其不可以热插拔。

### Thermal engine

* 完整的温度保护策略
* 对特定对象必须调节
* Thermal reset

#### Thermal 管理策略--嵌入式和配置方式

三种算法: SS（single step），PID（Proportional-Integral-Derivative）和monitor。

所有的规则要么定义于config文件，要么硬编码到thermal engine 数据文件里。
对于嵌入式规则，要求必须提供电压限制和PSM控制，可选的是所有CPU启动SS/PID控制算法, 对于配置文件定义的算法，管理结温的算法通常采用SS算法，管理Pop 内存的采用SS或者monitor算法。

* SS algorithm
本例中label是surface_control_dtm，该名称必须是唯一的，SS算法(algo_type = ss)通过每隔一秒(sampling = 1000)采样ID是3(sensor = tsens_tz_sensor3)的传感器来进行温度控制，DTM控制所有CPU的最大允许的主频(device = cpu)，Sensor ID 3的温度设置根据系统表面温度是25°时设定的，所以控制表面温度在45°时，ID 3的温度传感器的温度应该在70°，安全门限温度设置在了55°，这一温度将不会限制CPU主频。

```
[surface_control_dtm]
algo_type ss
sensor tsens_tz_sensor3
device cpu
sampling 1000
set_point 70000
set_point_clr 55000
```

* PID algorithm

标号是urface_control_pid，算法类型是(algo_type = pid)，其65ms(sampling = 65)间隔采样ID 5温度传感器(sensor = tsens_tz_sensor5)，PID调节最大允许的cpu主频(device = cpu0)，ID 5是CPU0，所以控制温度设置成了95°，安全门限温度是55°，所谓的安全门限就是在这一温度以下，主频可以放开跑。

```
[CPU0_control_pid]
algo_type pid
sensor tsens_tz_sensor5
device cpu0
sampling 65
set_point 95000
set_point_clr 55000
```

* Monitor algorithm

```
[modem]
algo_type monitor
sensor pa_therm0
sampling 1000
thresholds 70000 80000
thresholds_clr 65000 75000
actions modem mode
```

#### Thermal engine 调试

1.找到当前的thermal engine配置

```
adb shell thermal-engine -o > thermal-engine.conf
```

2.修改该文件后推送到设备上
3.将”debug“放到thermal-engine.conf的首行，然后重新启动thermal-engine服务

```
adb shell stop thermal-engine
adb root
adb remount
adb push thermal-engine.conf /system/etc/thermal-engine.conf
adb shell sync .
adb shell strat thermal-engine --debug &
```

4.logcat查看系统温度log

```
adb logcat –v time –s ThermalEngine
```

#### Temperature日志

1.绝大多数信息已经导入到了sysfs node节点里。
2.log脚本周期性记录温度并保存到文件里
3.当前主频和最高主频也要记录到文件里

```
// checking for temp zone 0 value if sensor available
if (tz_flags[0]) {
tz_temp= 0;
tzs=
fopen("/sys/devices/virtual/thermal/thermal_zone0/temp","r");
if(tzs) {
fscanf(tzs,"%d",&tz_temp);
if (debug) {
printf("\nReadTEMPZONE0
file %d\n",tz_temp);
}
fclose(tzs);
}
fprintf(out_fd,"%d,",tz_temp);
}
```

4.POP内存/表面温度记录

可以使用热电偶或者红外摄像机

5.读取当前的温度

```
adb shell cat /sys/devices/virtual/thermal/thermal_*/temp
```

原文链接：https://blog.csdn.net/shichaog/article/details/60959260

configuration:

https://blog.csdn.net/finewind/article/details/47016683