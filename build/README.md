## 构建任务(Build Tasks)

java和Android通用的任务
在build文件中使用了Android或者Java插件之后就会自动创建一系列可以运行的任务。

Gradle中有如下一下默认约定的任务:

### 1. assemble

该任务包含了项目中的所有打包相关的任务，比如java项目中打的jar包，Android项目中打的apk

### 2. check

该任务包含了项目中所有验证相关的任务，比如运行测试的任务

### 3. build

该任务包含了assemble和check

### 4. clean

该任务会清空项目的所有的输出，删除所有在assemble任务中打的包
