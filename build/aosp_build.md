# aosp build

## 设置 Mac OS 编译环境

在默认安装过程中，macOS 会在一个保留大小写但不区分大小写的文件系统中运行。Git 不支持这种类型的文件系统，而且此类文件系统会导致某些 Git 命令（如 git status）的行为出现异常。因此，我们建议您始终在区分大小写的文件系统中处理 AOSP 源代码文件。使用下文中介绍的磁盘映像可以非常轻松地做到这一点。

有了适当的文件系统，在新型 macOS 环境中编译 master 分支就会变得非常简单。要编译较早版本的分支，则需要一些额外的工具和 SDK。

### 创建区分大小写的磁盘映像

您可以使用磁盘映像在现有的 macOS 环境中创建区分大小写的文件系统。要创建磁盘映像，请启动磁盘工具，然后选择 New Image。完成编译至少需要 25GB 空间；更大的空间能够更好地满足未来的增长需求。使用稀疏映像有助于节省空间，同时可以根据需要进行扩展。请选择 Case sensitive, Journaled 卷格式。

您也可以通过 shell 使用以下命令创建文件系统：

```
hdiutil create -type SPARSE -fs 'Case-sensitive Journaled HFS+' -size 40g ~/android.dmg
```

这将创建一个 .dmg（也可能是 .dmg.sparseimage）文件，该文件在装载后可用作具有 Android 开发所需格式的驱动程序。

如果您以后需要更大的卷，可以使用以下命令来调整稀疏映像的大小：

```
hdiutil resize -size <new-size-you-want>g ~/android.dmg.sparseimage
```


对于存储在主目录下的名为 android.dmg 的磁盘映像，您可以向 ~/.bash_profile 中添加帮助程序函数：

要在执行 mountAndroid 时装载映像，请运行以下命令：

```
# mount the android file image
mountAndroid() { hdiutil attach ~/android.dmg -mountpoint /Volumes/android; }
```

注意：如果系统创建的是 .dmg.sparseimage 文件，请将 ~/android.dmg 替换为 ~/android.dmg.sparseimage。
要在执行 umountAndroid 时卸载映像，请运行以下命令：

```
# unmount the android file image
    umountAndroid() { hdiutil detach /Volumes/android; }
```

装载 android 卷后，您将在其中开展所有工作。您可以像对待外接式驱动盘一样将其弹出（卸载）。

### 安装 Xcode 和其他软件包

使用以下命令安装 Xcode 命令行工具：

```
xcode-select --install
```

对于旧版 macOS（10.8 或更低版本），您必须从 Apple 开发者网站安装 Xcode。如果您尚未注册成为 Apple 开发者，则必须创建一个 Apple ID 才能下载。
安装 MacPorts 或 Homebrew 以进行软件包管理。
确保关联的目录位于 ~/.bash_profile 文件的路径中：
MacPorts - /opt/local/bin 必须显示在 /usr/bin 之前：

```
export PATH=/opt/local/bin:$PATH
```

Homebrew - /usr/local/bin：

```
export PATH=/usr/local/bin:$PATH
```

如果使用 MacPorts，请发出：

```
POSIXLY_CORRECT=1 sudo port install git gnupg
```

如果使用 Homebrew，请发出：

```
brew install git gnupg2
```

设置文件描述符数量上限
在 macOS 中，可同时打开的文件描述符的默认数量上限太低，在高度并行的编译流程中，可能会超出此上限。要提高此上限，请将下列行添加到 ~/.bash_profile 中：

```
# set the number of open files to be 1024
ulimit -S -n 1024
```