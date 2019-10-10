# verity key

## x509

```
$ mkdir -p /home/work/verity-testkey/
$ cd到代码根目录
$ development/tools/make_key /home/work/verity-testkey/verity "/C=CN/ST=Beijing/L=Beijing/O=Xiaomi/OU=MIUI/CN=MIUI/emailAddress=miui@xiaomi.com"
$ make -j24 generate_verity_key
$ out/host/linux-x86/bin/generate_verity_key -convert /home/work/verity-testkey/verity.x509.pem /home/work/verity-testkey/verity_key
$ cd /home/work/verity-testkey/
$ mv verity_key.pub verity_key
```

## keystore

x509 ==> keystore

```
第一步，得到rsa_public_key_der​格式文件
$ openssl pkcs8 -inform DER -nocrypt -in verity.pk8 -out verity_private.pem
$ openssl rsa -in verity_private.pem -pubout -outform DER -out rsa_public_key_der
第二步，得到keystore文件，其中xxx代表放key文件的路径
$ make -j24 keystore_signer
$ make -j24 BootKeystoreSigner
$ cd out/host/linux-x86/
$ bin/keystore_signer /xxx/verity.pk8 /xxx/verity.x509.pem /xxx/oem_keystore.img /xxx/rsa_public_key_der
```