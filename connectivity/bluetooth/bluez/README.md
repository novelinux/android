# Bluetooth

https://en.wikipedia.org/wiki/List_of_Bluetooth_protocols

* https://www.cnblogs.com/hzl6255/category/588989.html

## GATT

GATT(Generic Attribute Profile)，描述了一种使用ATT的服务框架
该框架定义了服务(Server)和服务属性(characteristic)的过程(Procedure)及格式
Procedure定义了characteristic的发现、读、写、通知(Notifing)、指示(Indicating)及配置characteristic的广播

GATT可以被Application或其他Profile使用，其协议栈如下图

---------------        ---------------
| Application | <----> | Application |
---------------        ---------------
|  Attribute  |        | Attribute   |
|  Protocol   | <----> | Protocol    |
---------------        ---------------
|    L2CAP    | <----> |    L2CAP    |
---------------        ---------------
|  Controller | <----> |  Controller |
---------------        ---------------

GATT可以配置为如下两种角色(Role)

```
- Client : 命令、请求发起方
- Server : 命令、请求接收方
```

* https://www.cnblogs.com/hzl6255/p/4158363.html

## ATT

ATT，Attribute Protocol，用于发现、读、写对端设备的协议(针对BLE设备)

ATT允许设备作为服务端提供拥有关联值的属性集
让作为客户端的设备来发现、读、写这些属性；同时服务端能主动通知客户端

ATT定义了两种角色: 服务端(Server)和客户端(Client)

ATT中的属性包含下面三个内容

```
 - Attribute Type       : 由UUID(Universally Unique IDentifier)来定义
 - Attribute Handle     : 用来访问Attribute Value
 - A set of Permissions : 控制是否该Attribute可读、可写、属性值是否通过加密链路发送
```

一个设备可以同时拥有Server和Client；而一个Server可以支持多个Client

* https://www.cnblogs.com/hzl6255/p/4141505.html

## RFCOMM

* https://www.cnblogs.com/hzl6255/p/4158363.html

## GAP

GAP，Generic Access Profile，该Profile保证不同的Bluetooth产品可以互相发现对方并建立连接

GAP定义了蓝牙设备如何发现和建立与其他设备的安全/不安全连接
它处理一些一般模式的业务(如询问、命名和搜索)和一些安全性问题(如担保)
同时还处理一些有关连接的业务(如链路建立、信道和连接建立)

GAP规定的是一些一般性的运行任务；因此，它具有强制性，并作为所有其它蓝牙应用规范的基础

## Tools

### hcitool scan for bluetooth devices

Before start scanning make sure that your bluetooth device is turned on and not blocked, you can check that with the rfkill command:

```
rfkill list
```

If the bluetooth device is blocked (soft or hard blocked), unblock it with the rfkill command again:

```
rfkill unblock bluetooth
```

Bring up the bluetooth device with hciconfig command and start scanning, make sure the target device's bluetooth is on and It's discoverable:

```
hciconfig hci0 up
hcitool scan
```

note: use hcitool lescan will forever scan ble devices, if use ctrl+c stop it, it will show error(ref to LINKS 4 to solve):

```
hcitool lescan
```

bluetooth service discovery
Now we have the bluetooth MAC address of the target device, use the sdptool command to know which services (like DUN, Handsfree audio) are available on that target device.

```
sdptool browse 28:ED:6A:A0:26:B7
```

You can also use the interactive bluetoothctl tool for this purpose.

If the target device is present, you can ping it with l2ping command, requires root privilege:

```
l2ping 94:87:E0:B3:AC:6F
```

```
btmgmt -i hci0 power off
btmgmt -i hci0 le on
btmgmt -i hci0 connectable on
btmgmt -i hci0 bredr off        # Disables BR/EDR !
btmgmt -i hci0 advertising on
btmgmt -i hci0 power on
```

```
btgatt-server -i hci0 -s low -t public -r -v
```