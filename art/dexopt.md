## dexopt

APK在安装的时候，安装服务PackageManagerService会通过守护进程installd调用一个工具dexopt对打包在APK里面包含有Dex字节码的classes.dex进行优化，优化得到的文件保存在/data/dalvik-cache目录中，并且以.odex为后缀名，表示这是一个优化过的Dex文件。在ART运行时中，APK在安装的时候，同样安装服务PackageManagerService会通过守护进程installd调用另外一个工具dex2oat对打包在APK里面包含有Dex字节码进翻译。这个翻译器实际上就是基于LLVM架构实现的一个编译器，它的前端是一个Dex语法分析器。翻译后得到的是一个ELF格式的oat文件，这个oat文件同样是以.odex后缀结束，并且也是保存在/data/dalvik-cache目录中。

[Dexopt Flow](./dexopt.png)

* .dex: 存储java字节码
* .odex/.oat: optimized dex，ELF格式
* .vdex: verified dex，包含 raw dex +（quicken info)
* .art: image文件，存储热点方法string, method, types等

```
/oat/arm # ls -lh
total 43M
-rw-r----- 1 system all_a172 3.8M 2019-11-26 11:06 base.art
-rw-r----- 1 system all_a172  15M 2019-11-26 11:03 base.odex
-rw-r----- 1 system all_a172  67M 2019-11-26 11:06 base.vdex
```

### base.vdex

```
whyred:/proc/15696 # cat maps | grep base.vdex
76e99000-76ea1000 r--s 00000000 fc:00 1248                               /system/framework/boot-android.test.base.vdex
d210c000-d63ee000 r--s 00000000 fc:02 1705210                            /data/app/com.ss.android.ugc.aweme-ZmOCKX8MRb9xcFJYl-abMg==/oat/arm/base.vdex
```

### base.odex

```
whyred:/proc/15696 # cat maps | grep base.odex
d1285000-d1776000 r--p 00000000 fc:02 1705209                            /data/app/com.ss.android.ugc.aweme-ZmOCKX8MRb9xcFJYl-abMg==/oat/arm/base.odex

// 004f1000 <oatexec>:
d1776000-d20f5000 r-xp 004f1000 fc:02 1705209                            /data/app/com.ss.android.ugc.aweme-ZmOCKX8MRb9xcFJYl-abMg==/oat/arm/base.odex

// 00e70000 <oatbss>:
d63ee000-d63ef000 r--p 00e70000 fc:02 1705209                            /data/app/com.ss.android.ugc.aweme-ZmOCKX8MRb9xcFJYl-abMg==/oat/arm/base.odex

d63ef000-d63f0000 rw-p 00e71000 fc:02 1705209                            /data/app/com.ss.android.ugc.aweme-ZmOCKX8MRb9xcFJYl-abMg==/oat/arm/base.odex
```

### base.art

```
whyred:/proc/15696 # cat maps | grep base.art
7b54c000-7c4a3000 rw-p 00000000 00:05 213199                             /dev/ashmem/dalvik-/data/app/com.ss.android.ugc.aweme-ZmOCKX8MRb9xcFJYl-abMg==/oat/arm/base.art (deleted)
```

### base.odex

```
$ arm-linux-androideabi-readelf -a base.odex
ELF Header:
  Magic:   7f 45 4c 46 01 01 01 03 00 00 00 00 00 00 00 00
  Class:                             ELF32
  Data:                              2's complement, little endian
  Version:                           1 (current)
  OS/ABI:                            UNIX - GNU
  ABI Version:                       0
  Type:                              DYN (Shared object file)
  Machine:                           ARM
  Version:                           0x1
  Entry point address:               0x0
  Start of program headers:          52 (bytes into file)
  Start of section headers:          15460372 (bytes into file)
  Flags:                             0x5000000, Version5 EABI
  Size of this header:               52 (bytes)
  Size of program headers:           32 (bytes)
  Number of program headers:         8
  Size of section headers:           40 (bytes)
  Number of section headers:         11
  Section header string table index: 10

Section Headers:
  [Nr] Name              Type            Addr     Off    Size   ES Flg Lk Inf Al
  [ 0]                   NULL            00000000 000000 000000 00      0   0  0
  [ 1] .rodata           PROGBITS        00001000 001000 4f0000 00   A  0   0 4096
  [ 2] .text             PROGBITS        004f1000 4f1000 97ecfc 00  AX  0   0 4096
  [ 3] .bss              NOBITS          00e70000 000000 016834 00   A  0   0 4096
  [ 4] .dex              NOBITS          00e87000 000000 42e1588 00   A  0   0 4096
  [ 5] .dynstr           STRTAB          05169000 e70000 00006d 00   A  0   0 4096
  [ 6] .dynsym           DYNSYM          05169070 e70070 0000a0 10   A  5   1  4
  [ 7] .hash             HASH            05169110 e70110 000034 04   A  6   0  4
  [ 8] .dynamic          DYNAMIC         0516a000 e71000 000038 08   A  5   0 4096
  [ 9] .gnu_debugdata    PROGBITS        00000000 e72000 04c7c0 00      0   0 4096
  [10] .shstrtab         STRTAB          00000000 ebe7c0 000051 00      0   0  1
Key to Flags:
  W (write), A (alloc), X (execute), M (merge), S (strings), I (info),
  L (link order), O (extra OS processing required), G (group), T (TLS),
  C (compressed), x (unknown), o (OS specific), E (exclude),
  y (noread), p (processor specific)

There are no section groups in this file.

Program Headers:
  Type           Offset   VirtAddr   PhysAddr   FileSiz MemSiz  Flg Align
  PHDR           0x000034 0x00000034 0x00000034 0x00100 0x00100 R   0x4
  LOAD           0x000000 0x00000000 0x00000000 0x4f1000 0x4f1000 R   0x1000
  LOAD           0x4f1000 0x004f1000 0x004f1000 0x97ecfc 0x97ecfc R E 0x1000
  LOAD           0x000000 0x00e70000 0x00e70000 0x00000 0x16834 RW  0x1000
  LOAD           0x000000 0x00e87000 0x00e87000 0x00000 0x42e1588 R   0x1000
  LOAD           0xe70000 0x05169000 0x05169000 0x00144 0x00144 R   0x1000
  LOAD           0xe71000 0x0516a000 0x0516a000 0x00038 0x00038 RW  0x1000
  DYNAMIC        0xe71000 0x0516a000 0x0516a000 0x00038 0x00038 RW  0x1000

 Section to Segment mapping:
  Segment Sections...
   00
   01     .rodata
   02     .text
   03     .bss
   04     .dex
   05     .dynstr .dynsym .hash
   06     .dynamic
   07     .dynamic

Dynamic section at offset 0xe71000 contains 7 entries:
  Tag        Type                         Name/Value
 0x00000004 (HASH)                       0x5169110
 0x00000005 (STRTAB)                     0x5169000
 0x00000006 (SYMTAB)                     0x5169070
 0x0000000b (SYMENT)                     16 (bytes)
 0x0000000a (STRSZ)                      109 (bytes)
 0x0000000e (SONAME)                     Library soname: [base.odex]
 0x00000000 (NULL)                       0x0

There are no relocations in this file.

There are no unwind sections in this file.

Symbol table '.dynsym' contains 10 entries:
   Num:    Value  Size Type    Bind   Vis      Ndx Name
     0: 00000000     0 NOTYPE  LOCAL  DEFAULT  UND
     1: 00001000 0x4f0000 OBJECT  GLOBAL DEFAULT    1 oatdata
     2: 004f1000     0 OBJECT  GLOBAL DEFAULT    2 oatexec
     3: 00e6fcf8     4 OBJECT  GLOBAL DEFAULT    2 oatlastword
     4: 00e70000 28148 OBJECT  GLOBAL DEFAULT    3 oatbss
     5: 00e70000 28148 OBJECT  GLOBAL DEFAULT    3 oatbssmethods
     6: 00e76df4 64064 OBJECT  GLOBAL DEFAULT    3 oatbssroots
     7: 00e86830     4 OBJECT  GLOBAL DEFAULT    3 oatbsslastword
     8: 00e87000     0 OBJECT  GLOBAL DEFAULT    4 oatdex
     9: 05168584     4 OBJECT  GLOBAL DEFAULT    4 oatdexlastword

Histogram for bucket list length (total of 1 buckets):
 Length  Number     % of total  Coverage
      0  1          (100.0%)

No version information found in this file.
```

[base.odex elf format](./odex.jpeg)

在oat文件的动态段（dymanic section）中，还导出了三个符号oatdata、oatexec和oatlastword，分别用来描述oatdata和oatexec段加段到内存后的起止地址。在oatdata段中，包含了两个重要的信息，一个信息是原来的classes.dex文件的完整内容，另一个信息引导ART找到classes.dex文件里面的类方法所对应的本地机器指令，这些本地机器指令就保存在oatexec段中。

举个例子说，我们在classes.dex文件中有一个类A，那么当我们知道类A的名字后，就可以通过保存在oatdata段的dex文件得到类A的所有信息，比如它的父类、成员变量和成员函数等。另一方面，类A在oatdata段中有一个对应的OatClass结构体。这个OatClass结构体描述了类A的每一个方法所对应的本地机器指令在oatexec段的位置。也就是说，当我们知道一个类及其某一个方法的名字（签名）之后，就可以通过oatdata段的dex文件内容和OatClass结构体找到其在oatexec段的本地机器指令，这样就可以执行这个类方法了。
