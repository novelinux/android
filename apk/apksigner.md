## Android 签名方式V1、V2（Mac）

### 签名使用

v1、v2签名方式选择
App打包时v1和v2签名可在Build/Generate Signed APK选项下手动勾选（已生成jks文件），也可在build.gradle进行配置v1SigningEnabled和v2SigningEnabled，示例如下

```
    signingConfigs {
        release {
            keyAlias 'test'
            keyPassword 'test'
            storeFile file('./keystore/test.jks')
            storePassword 'test'
            v1SigningEnabled true
            v2SigningEnabled true
        }
        debug {
            keyAlias 'test'
            keyPassword 'test'
            storeFile file('./keystore/test.jks')
            storePassword 'test'
            v1SigningEnabled true
            v2SigningEnabled true
        }
    }
```

使用jarsigner进行v1签名

```
签名命令：jarsigner -verbose -keystore xxx.jks -signedjar xxx.apk（签名后的apk名字） xxx.apk（需要签名的apk） xxx（keystore别名)
签名示例：jarsigner -verbose -keystore test.jks -signedjar test-signed.apk test.apk hrdbank
```

### 使用apksigner进行v2签名

从官网关于apksigner的介绍来看，签名前需使用zipalign对齐工具进行包对其，引用官网警告

警告:如果您在使用 apksigner 为 APK 签名后对 APK 进行进一步更改，则 APK 的签名将会失效。因此，在为 APK 签名之前，您必须使用 zipalign 等工具。

对齐命令：zipalign -v 4 infile.apk outfile.apk
说明：对齐 infile.apk 并将其保存为 outfile.apk
对齐验证：zipalign -c -v 4 existing.apk
说明：确认 existing.apk 的对齐方式
签名命令：apksigner sign --ks 密钥库名 --ks-key-alias 密钥别名 --out 签名后apk 需要签名的apk
签名示例：apksigner sign --ks test.jks --ks-key-alias test --out sign.apk zipalign.apk

完整示例：

```
 zipalign -v 4 test.apk zipalign.apk     //对齐
 zipalign -c -v 4 zipalign.apk           //对齐验证
 apksigner sign --ks test.jks --ks-key-alias test --out sign.apk zipalign.apk //签名
 apksigner verify -v sign.apk           // 签名验证
``

### 签名验证

使用apksigner验证Apk签名是否成功
进入build-tools具体sdk版本下
使用 apksigner verify -v --print-certs xxx.apk查看所需信息，参数说明如下:
-v, --verbose 显示详情(显示是否使用V1和V2签名)
--print-certs 显示签名证书信息
示例如下（成功与未签名）

```
apksigner verify -v /Users/eminem/Desktop/demo.apk
```

### Verifies

Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1

```
apksigner verify -v /Users/eminem/Desktop/JavaProtectorClient2/workspace/output/unsigned.apk
ERROR: JAR signer CERT.RSA: JAR signature META-INF/CERT.SF indicates the APK is signed using APK Signature Scheme v2 but no such signature was found. Signature stripped?
```
