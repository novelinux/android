# Generate Implementation

* 1.Generate an ext4 system image.
* 2.Generate a hash tree for that image.
* 3.Build a dm-verity table for that hash tree.
* 4.Sign that dm-verity table to produce a table signature.
* 5.Bundle the table signature and dm-verity table into verity metadata.
* 6.Concatenate the system image, the verity metadata, and the hash tree.

path: build/tools/releasetools/build_image.py
```
BuildImage
 |
MakeVerityEnabledImage
 |
 +-> BuildVerityTree (Generate the hash tree)
 |
 +-> BuildVerityMetadata -> build_verity_metadata
 |   |
 |   +-> build_verity_table
 |   |
 |   +-> sign_verity_table
 |   |
 |   +-> build_metadata_block
 |
 +-> BuildVerifiedImage
 |   |
 |   +-> BuildVerityFEC
 |   |
 |   +-> Append
 |   |
 |   +-> Append2Simg
 |
END
```

* Build Logs

```
Running ['mkuserimg.sh', '-s', '/tmp/tmphUpYR9', 'out/target/product/tissot/obj/PACKAGING/systemimage_intermediates/system.img', 'ext4', '/', '3170938880', '-D', 'out/target/product/tissot/system', '-L', '/', 'out/target/product/tissot/root/file_contexts.bin'] command, exit code = 0
build_verity_tree -A aee087a5be3b982978c923f566a94613496b417f2af592639bc80d141e34dfe7 out/target/product/tissot/obj/PACKAGING/systemimage_intermediates/system.img /tmp/tmp9TKPlY_verity_images/verity.img
system/extras/verity/build_verity_metadata.py 3170938880 /tmp/tmp9TKPlY_verity_images/verity_metadata.img d1c859811f935a0b299e3f584820e1ffbbaf37893e59c54800ae8f4719501f1f aee087a5be3b982978c923f566a94613496b417f2af592639bc80d141e34dfe7 /dev/block/bootdevice/by-name/system verity_signer build/target/product/security/verity.pk8
cat /tmp/tmp9TKPlY_verity_images/verity_metadata.img >> /tmp/tmp9TKPlY_verity_images/verity.img
fec -e out/target/product/tissot/obj/PACKAGING/systemimage_intermediates/system.img /tmp/tmp9TKPlY_verity_images/verity.img /tmp/tmp9TKPlY_verity_images/verity_fec.img
cat /tmp/tmp9TKPlY_verity_images/verity_fec.img >> /tmp/tmp9TKPlY_verity_images/verity.img
append2simg out/target/product/tissot/obj/PACKAGING/systemimage_intermediates/system.img /tmp/tmp9TKPlY_verity_images/verity.img
[ 99% 12153/12154] Install system fs image: out/target/product/tissot/system.img
out/target/product/tissot/system.img+ maxsize=3288637440 blocksize=135168 total=2812839568 reserve=33251328
```

## Generate an ext4 system image

### fs_config

```
$ cat misc_info.txt
recovery_api_version=3
fstab_version=2
blocksize=131072
boot_size=0x04000000
recovery_as_boot=
recovery_size=0x04000000
has_ext4_reserved_blocks=true
recovery_mount_options=ext4=max_batch_time=0,commit=1,data=ordered,barrier=1,errors=panic,nodelalloc
tool_extensions=device/xiaomi/gemini_common
default_system_dev_certificate=build/target/product/security/testkey
mkbootimg_args=
mkbootimg_version_args=--os_version 7.0 --os_patch_level 2017-01-01
use_set_metadata=1
multistage_support=1
update_rename_support=1
blockimgdiff_versions=1,2,3,4
fs_type=ext4
system_size=3221225472
has_ext4_reserved_blocks=true
userdata_size=10737418240
cache_fs_type=ext4
cache_size=268435456
cust_fs_type=ext4
cust_size=872415232
extfs_sparse_flag=-s
squashfs_sparse_flag=-s
selinux_fc=out/target/product/gemini/root/file_contexts.bin
boot_signer=true
verity=true
verity_key=build/target/product/security/verity
verity_signer_cmd=verity_signer
verity_fec=true
system_verity_block_device=/dev/block/bootdevice/by-name/system
```

### BuildImage

```
def BuildImage(in_dir, prop_dict, out_file, target_out=None):
  """Build an image to out_file from in_dir with property prop_dict.

  Args:
    in_dir: path of input directory.
    prop_dict: property dictionary.
    out_file: path of the output image file.
    target_out: path of the product out directory to read device specific FS config files.

  Returns:
    True iff the image is built successfully.
  """

  ...

  is_verity_partition = "verity_block_device" in prop_dict
  verity_supported = prop_dict.get("verity") == "true"
  verity_fec_supported = prop_dict.get("verity_fec") == "true"

  # Adjust the partition size to make room for the hashes if this is to be
  # verified.
  if verity_supported and is_verity_partition:
    partition_size = int(prop_dict.get("partition_size"))
    adjusted_size = AdjustPartitionSizeForVerity(partition_size,
                                                 verity_fec_supported)
    if not adjusted_size:
      print "Error: adjusting partition size for verity failed, partition_size = %d" % partition_size
      return False
    prop_dict["partition_size"] = str(adjusted_size)
    prop_dict["original_partition_size"] = str(partition_size)

  ...

  # create the verified image if this is to be verified
  if verity_supported and is_verity_partition:
    if not MakeVerityEnabledImage(out_file, verity_fec_supported, prop_dict):
      print "Error: making verity enabled image failed"
      return False
```

### MakeVerityEnabledImage

```
def MakeVerityEnabledImage(out_file, fec_supported, prop_dict):
  """Creates an image that is verifiable using dm-verity.

  Args:
    out_file: the location to write the verifiable image at
    prop_dict: a dictionary of properties required for image creation and
               verification
  Returns:
    True on success, False otherwise.
  """
  # get properties
  image_size = prop_dict["partition_size"]
  block_dev = prop_dict["verity_block_device"]
  signer_key = prop_dict["verity_key"] + ".pk8"
  if OPTIONS.verity_signer_path is not None:
    signer_path = OPTIONS.verity_signer_path + ' '
    signer_path += ' '.join(OPTIONS.verity_signer_args)
  else:
    signer_path = prop_dict["verity_signer_cmd"]

  # make a tempdir
  tempdir_name = tempfile.mkdtemp(suffix="_verity_images")

  # get partial image paths
  verity_image_path = os.path.join(tempdir_name, "verity.img")
  verity_metadata_path = os.path.join(tempdir_name, "verity_metadata.img")
  verity_fec_path = os.path.join(tempdir_name, "verity_fec.img")

  # build the verity tree and get the root hash and salt
  if not BuildVerityTree(out_file, verity_image_path, prop_dict):
    shutil.rmtree(tempdir_name, ignore_errors=True)
    return False

  # build the metadata blocks
  root_hash = prop_dict["verity_root_hash"]
  salt = prop_dict["verity_salt"]
  if not BuildVerityMetadata(image_size, verity_metadata_path, root_hash, salt,
                             block_dev, signer_path, signer_key):
    shutil.rmtree(tempdir_name, ignore_errors=True)
    return False

  # build the full verified image
  if not BuildVerifiedImage(out_file,
                            verity_image_path,
                            verity_metadata_path,
                            verity_fec_path,
                            fec_supported):
    shutil.rmtree(tempdir_name, ignore_errors=True)
    return False

  shutil.rmtree(tempdir_name, ignore_errors=True)
  return True
```

### Generating the hash tree

To form the hash, the system image is split at layer 0 into 4k blocks,
each assigned a SHA256 hash. Layer 1 is formed by joining only those
SHA256 hashes into 4k blocks, resulting in a much smaller image.
Layer 2 is formed identically, with the SHA256 hashes of Layer 1.

This is done until the SHA256 hashes of the previous layer can fit
in a single block. When get the SHA256 of that block, you have the
root hash of the tree.

The size of the hash tree (and corresponding disk space usage) varies
with the size of the verified partition. In practice, the size of
hash trees tends to be small, often less than 30 MB.

If you have a block in a layer that isn't completely filled naturally
by the hashes of the previous layer, you should pad it with zeroes to
achieve the expected 4k. This allows you to know the hash tree hasn't
been removed and is instead completed with blank data.

To generate the hash tree, concatenate the layer 2 hashes onto those
for layer 1, the layer 3 the hashes onto those of layer 2, and so on.
Write all of this out to disk. Note that this doesn't reference
layer 0 of the root hash.

To recap, the general algorithm to construct the hash tree is as follows:

* 1.Choose a random salt (hexadecimal encoding).
* 2.Unsparse your system image into 4k blocks.
* 3.For each block, get its (salted) SHA256 hash.
* 4.Concatenate these hashes to form a level
* 5.Pad the level with 0s to a 4k block boundary.
* 6.Concatenate the level to your hash tree.
Repeat steps 2-6 using the previous level as the source for the next until
you have only a single hash.

The result of this is a single hash, which is your root hash. This and your salt
are used during the construction of your dm-verity mapping hash table.

#### BuildVerityTree

```
def BuildVerityTree(sparse_image_path, verity_image_path, prop_dict):
  cmd = "build_verity_tree -A %s %s %s" % (
      FIXED_SALT, sparse_image_path, verity_image_path)
  print cmd
  status, output = commands.getstatusoutput(cmd)
  if status:
    print "Could not build verity tree! Error: %s" % output
    return False
  root, salt = output.split()
  prop_dict["verity_root_hash"] = root
  prop_dict["verity_salt"] = salt
  return True
```

通过build_verity_tree 这个程序去构建hash tree，所以构建hash tree 的算法就在这个
build_verity_tree.cpp 文件中， 那么算法怎么实现的，就不去管它了。有点复杂。至于使用
hash tree的原因官网上也提到了，最初他使用hash table来实现，后来数据太大之后，效率不好，
所以使用hash tree，hash tree在处理大量数据的时候效率就非常高。产生的hash tree的结构就如下所示:

Each node in the tree is a cryptographic hash.  If it is a leaf node, the hash
of some data block on disk is calculated. If it is an intermediary node,
the hash of a number of child nodes is calculated.

Each entry in the tree is a collection of neighboring nodes that fit in one
block.  The number is determined based on block_size and the size of the
selected cryptographic digest algorithm.  The hashes are linearly-ordered in
this entry and any unaligned trailing space is ignored but included when
calculating the parent node.

The tree looks something like:

```
alg = sha256, num_blocks = 32768, block_size = 4096

                                 [   root    ]
                                /    . . .    \
                     [entry_0]                 [entry_1]
                    /  . . .  \                 . . .   \
         [entry_0_0]   . . .  [entry_0_127]    . . . .  [entry_1_127]
           / ... \             /   . . .  \             /           \
     blk_0 ... blk_127  blk_16256   blk_16383      blk_32640 . . . blk_32767
```


最后只有一个根hash，叶节点就是dm-verity所需要verify的分区的划分的一个个小块，它这里规定了
每个块以4k的大小来划分。所以举个例子，要验证的system分区如果有800M，那么就有200万个块。
所以说通过叶节点以及他的父hash到根hash就是描述了system.img的变化情况。这样的话用
hash table来存效率就很差，所以使用hash tree来存速度更快。最后呢再主要保存这个root hash。

### BuildVerityMetadata

```
def BuildVerityMetadata(image_size, verity_metadata_path, root_hash, salt,
                        block_device, signer_path, key):
  cmd_template = (
      "system/extras/verity/build_verity_metadata.py %s %s %s %s %s %s %s")
  cmd = cmd_template % (image_size, verity_metadata_path, root_hash, salt,
                        block_device, signer_path, key)
  print cmd
  status, output = commands.getstatusoutput(cmd)
  if status:
    print "Could not build verity metadata! Error: %s" % output
    return False
  return True
```

可以看到这段代码最终调用到了build_verity_metadata.py这个脚本

### build_verity_metadata

```
def build_verity_metadata(data_blocks, metadata_image, root_hash,
                            salt, block_device, signer_path, signing_key):
    # build the verity table
    verity_table = build_verity_table(block_device, data_blocks, root_hash, salt)
    # build the verity table signature
    signature = sign_verity_table(verity_table, signer_path, signing_key)
    # build the metadata block
    metadata_block = build_metadata_block(verity_table, signature)
    # write it to the outfile
    with open(metadata_image, "wb") as f:
        f.write(metadata_block)
```

### Build a dm-verity table for that hash tree

Build the dm-verity mapping table, which identifies the block device (or target)
for the kernel and the location of the hash tree (which is the same value.)
This mapping is used for fstab generation and booting. The table also identifies
the size of the blocks and the hash_start, or the offset in hash size
blocks (length of layer 0).

#### build_verity_table

```
def build_verity_table(block_device, data_blocks, root_hash, salt):
    table = "1 %s %s %s %s %s %s sha256 %s %s"
    table %= (  block_device,
                block_device,
                BLOCK_SIZE,
                BLOCK_SIZE,
                data_blocks,
                data_blocks,
                root_hash,
                salt)
    return table
```

verity-table就是为了去描述之前生成的hash tree,所以建立起来的verity table形如下面这样。
说白了，verity-table只是一个描述hashtree的字符串，看一看他是如何描述hash tree的。

* 第一个参数是版本，只有0和1，大多数情况下填1，不去深究。
* 第二个，第三个参数描述的是所保护的分区，这个例子中dm-verity保护的分区是/dev/sda1。
* 第四，第五个参数描述的该分区的每个block即hash tree的叶节点的大小，可以看到这里是4k，就是说，以4k为大小划分/dev/sda1为若干个区域。
* 第六，第七个参数描述了总共有多少个block，也就是hash tree有多少个叶节点。
* 第八个参数是hash 加密算法，这里使用sha256算法。
* 第九个参数是hash tree的根hash。
* 第十个参数是加密算法加的盐。
Ok，到这我们可以看到verity-table描述了叶节点和根hash以及hash的算法等。这样就通过一个字符串就把整棵树的形状就描绘出来了。

* Documentation: linux/Documentation/device-mapper/verity.txt

### Sign that dm-verity table to produce a table signature.

Sign the dm-verity table to produce a table signature.
When verifying a partition, the table signature is validated first.
This is done against a key on your boot image in a fixed location.
Keys are typically included in the manufacturers' build systems for
automatic inclusion on devices in a fixed location.

To verify the partition with this signature and key combination:

Add an RSA-2048 key in libmincrypt-compatible format to
the /boot partition at /verity_key. Identify the location of
the key used to verify the hash tree.

In the fstab for the relevant entry, add 'verify' to the fs_mgr flags.

#### sign_verity_table

```
def sign_verity_table(table, signer_path, key_path):
    with tempfile.NamedTemporaryFile(suffix='.table') as table_file:
        with tempfile.NamedTemporaryFile(suffix='.sig') as signature_file:
            table_file.write(table)
            table_file.flush()
            cmd = " ".join((signer_path, table_file.name, key_path, signature_file.name))
            print cmd
            run(cmd)
            return signature_file.read()
```

verity table建立完后，对他进行签名。签完名就把verity-table，签名信息和hash tree 一同写入到
metadata中，最后返回给build脚本。

### Bundle the table signature and dm-verity table into verity metadata

Bundle the table signature and dm-verity table into verity metadata.
The entire block of metadata is versioned so it may be extended,
such as to add a second kind of signature or change some ordering.

As a sanity check, a magic number is associated with each set of
table metadata that helps identify the table. Since the length
is included in the ext4 system image header, this provides a
way to search for the metadata without knowing the contents of the data itself.

This makes sure you haven't elected to verify an unverified partition.
If so, the absence of this magic number will halt the verification process.
This number resembles:

0xb001b001

The byte values in hex are:

first byte = b0
second byte = 01
third byte = b0
fourth byte = 01
The following diagram depicts the breakdown of the verity metadata:

```
<magic number>|<version>|<signature>|<table length>|<table>|<padding>
\-------------------------------------------------------------------/
\----------------------------------------------------------/   |
                            |                                  |
                            |                                 32K
                       block content
```
And this table describes those metadata fields.

Table 1. Verity metadata fields

```
Field             Purpose                                         Size    Value
magic number    used by fs_mgr as a sanity check                  4 bytes 0xb001b001
version         used to version the metadata block                4 bytes currently 0
signature       the signature of the table in PKCS1.5 padded form 256 bytes
table length    the length of the dm-verity table in bytes        4 bytes
table           the dm-verity table described earlier             `table length` bytes
padding         this structure is 0-padded to 32k in length       0
```

#### build_metadata_block

```
def build_metadata_block(verity_table, signature):
    table_len = len(verity_table)
    block = struct.pack("II256sI", MAGIC_NUMBER, VERSION, signature, table_len)
    block += verity_table
    block = block.ljust(METADATA_SIZE, '\x00')
    return block
```

### Concatenate the system image, the verity metadata, and the hash tree.

#### BuildVerifiedImage

```
def BuildVerifiedImage(data_image_path, verity_image_path,
                       verity_metadata_path, verity_fec_path,
                       fec_supported):
  if not Append(verity_image_path, verity_metadata_path,
                "Could not append verity metadata!"):
    return False

  if fec_supported:
    # build FEC for the entire partition, including metadata
    if not BuildVerityFEC(data_image_path, verity_image_path,
                          verity_fec_path):
      return False

    if not Append(verity_image_path, verity_fec_path, "Could not append FEC!"):
      return False

  if not Append2Simg(data_image_path, verity_image_path,
                     "Could not append verity data!"):
    return False
  return True
```

##### BuildVerityFEC

```
def BuildVerityFEC(sparse_image_path, verity_path, verity_fec_path):
  cmd = "fec -e %s %s %s" % (sparse_image_path, verity_path, verity_fec_path)
  print cmd
  status, output = commands.getstatusoutput(cmd)
  if status:
    print "Could not build FEC data! Error: %s" % output
    return False
  return True
```

##### Append2Simg

```
def Append2Simg(sparse_image_path, unsparse_image_path, error_message):
  """Appends the unsparse image to the given sparse image.

  Args:
    sparse_image_path: the path to the (sparse) image
    unsparse_image_path: the path to the (unsparse) image
  Returns:
    True on success, False on failure.
  """
  cmd = "append2simg %s %s"
  cmd %= (sparse_image_path, unsparse_image_path)
  print cmd
  status, output = commands.getstatusoutput(cmd)
  if status:
    print "%s: %s" % (error_message, output)
    return False
  return True
```