更低层次: virtualenv
virtualenv 是一个创建隔绝的Python环境的 工具。virtualenv创建一个包含所有必要的可执行文件的文件夹，用来使用Python工程所需的包。

它可以独立使用，代替Pipenv。

通过pip安装virtualenv：

$ pip install virtualenv
测试您的安装：

$ virtualenv --version
基本使用
为一个工程创建一个虚拟环境：
$ cd my_project_folder
$ virtualenv venv
virtualenv venv 将会在当前的目录中创建一个文件夹，包含了Python可执行文件， 以及 pip 库的一份拷贝，这样就能安装其他包了。虚拟环境的名字（此例中是 venv ） 可以是任意的；若省略名字将会把文件均放在当前目录。

在任何您运行命令的目录中，这会创建Python的拷贝，并将之放在叫做 venv 的文件中。

您可以选择使用一个Python解释器（比如``python2.7``）：

$ virtualenv -p /usr/bin/python2.7 venv
或者使用``~/.bashrc``的一个环境变量将解释器改为全局性的：

$ export VIRTUALENVWRAPPER_PYTHON=/usr/bin/python2.7
要开始使用虚拟环境，其需要被激活：
$ source venv/bin/activate
当前虚拟环境的名字会显示在提示符左侧（比如说 (venv)您的电脑:您的工程 用户名$） 以让您知道它是激活的。从现在起，任何您使用pip安装的包将会放在 ``venv 文件夹中， 与全局安装的Python隔绝开。

像平常一样安装包，比如：

$ pip install requests
如果您在虚拟环境中暂时完成了工作，则可以停用它：
$ deactivate
这将会回到系统默认的Python解释器，包括已安装的库也会回到默认的。

要删除一个虚拟环境，只需删除它的文件夹。（要这么做请执行 rm -rf venv ）

然后一段时间后，您可能会有很多个虚拟环境散落在系统各处，您将有可能忘记它们的名字或者位置。

其他注意事项
运行带 --no-site-packages 选项的 virtualenv 将不会包括全局安装的包。 这可用于保持包列表干净，以防以后需要访问它。（这在 virtualenv 1.7及之后是默认行为）

为了保持您的环境的一致性，“冷冻住（freeze）”环境包当前的状态是个好主意。要这么做，请运行：

$ pip freeze > requirements.txt
这将会创建一个 requirements.txt 文件，其中包含了当前环境中所有包及 各自的版本的简单列表。您可以使用 pip list 在不产生requirements文件的情况下， 查看已安装包的列表。这将会使另一个不同的开发者（或者是您，如果您需要重新创建这样的环境） 在以后安装相同版本的相同包变得容易。

$ pip install -r requirements.txt
这能帮助确保安装、部署和开发者之间的一致性。

最后，记住在源码版本控制中排除掉虚拟环境文件夹，可在ignore的列表中加上它。 （查看 版本控制忽略）