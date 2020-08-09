## Android Studio

发布组件到本地仓库：

```
apply plugin: 'maven'

...

uploadArchives {
    repositories.mavenDeployer {
        repository(url: "file:///Users/liminghao/.m2/repository/")
        pom.groupId = rootProject.ext.GROUP
        pom.artifactId = "matrix-gradle-plugin"
        pom.version = rootProject.ext.VERSION_NAME
    }
}
```

编译：

```
./gradlew -p matrix-gradle-plugin clean build uploadArchives --info
```
