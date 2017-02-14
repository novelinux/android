Android DM-verity
========================================

* https://source.android.com/security/verifiedboot/dm-verity.html

Generate Implementation
----------------------------------------

path: build/tools/releasetools/build_image.py

* 1.Generate an ext4 system image.
* 2.Generate a hash tree for that image.
* 3.Build a dm-verity table for that hash tree.
* 4.Sign that dm-verity table to produce a table signature.
* 5.Bundle the table signature and dm-verity table into verity metadata.
* 6.Concatenate the system image, the verity metadata, and the hash tree.

### Generate an ext4 system image

#### fs_config

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

#### BuildImage

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

#### MakeVerityEnabledImage

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

fs_mgr Implementation
----------------------------------------

### fs_mgr_mount_all

path: system/core/fs_mgr/fs_mgr.c
```
int fs_mgr_mount_all(struct fstab *fstab, int mount_mode)
{
        ...

        if ((fstab->recs[i].fs_mgr_flags & MF_VERIFY) && device_is_secure()) {
            int rc = fs_mgr_setup_verity(&fstab->recs[i]);
            if (device_is_debuggable() && rc == FS_MGR_SETUP_VERITY_DISABLED) {
                INFO("Verity disabled");
            } else if (rc != FS_MGR_SETUP_VERITY_SUCCESS) {
                ERROR("Could not set up verified partition, skipping!\n");
                continue;
            }
        }
        ...
}
```

### fs_mgr_setup_verity

#### struct fec_verity_metadata

path: system/extra/libfec/include/fec/io.h
```
struct fec_verity_metadata {
    bool disabled;
    uint64_t data_size;
    uint8_t signature[RSANUMBYTES];
    uint8_t ecc_signature[RSANUMBYTES];
    const char *table;
    uint32_t table_length;
};
```

#### fec

path: system/core/fs_mgr/fs_mgr_verity.cpp
```
struct verity_table_params {
    char *table;
    int mode;
    struct fec_ecc_metadata ecc;
    const char *ecc_dev;
};

int fs_mgr_setup_verity(struct fstab_rec *fstab)
{
    int retval = FS_MGR_SETUP_VERITY_FAIL;
    int fd = -1;
    char *verity_blk_name = NULL;
    struct fec_handle *f = NULL;
    struct fec_verity_metadata verity;
    struct verity_table_params params = { .table = NULL };

    alignas(dm_ioctl) char buffer[DM_BUF_SIZE];
    struct dm_ioctl *io = (struct dm_ioctl *) buffer;
    char *mount_point = basename(fstab->mount_point);

    if (fec_open(&f, fstab->blk_device, O_RDONLY, FEC_VERITY_DISABLE,
            FEC_DEFAULT_ROOTS) < 0) {
        ERROR("Failed to open '%s' (%s)\n", fstab->blk_device,
            strerror(errno));
        return retval;
    }

    // read verity metadata
    if (fec_verity_get_metadata(f, &verity) < 0) {
        ERROR("Failed to get verity metadata '%s' (%s)\n", fstab->blk_device,
            strerror(errno));
        goto out;
    }

#ifdef ALLOW_ADBD_DISABLE_VERITY
    if (verity.disabled) {
        retval = FS_MGR_SETUP_VERITY_DISABLED;
        INFO("Attempt to cleanly disable verity - only works in USERDEBUG\n");
        goto out;
    }
#endif

    // read ecc metadata
    if (fec_ecc_get_metadata(f, &params.ecc) < 0) {
        params.ecc.valid = false;
    }

    params.ecc_dev = fstab->blk_device;
```

#### create verity device

```
    // get the device mapper fd
    if ((fd = open("/dev/device-mapper", O_RDWR)) < 0) {
        ERROR("Error opening device mapper (%s)\n", strerror(errno));
        goto out;
    }

    // create the device
    if (create_verity_device(io, mount_point, fd) < 0) {
        ERROR("Couldn't create verity device!\n");
        goto out;
    }

    // get the name of the device file
    if (get_verity_device_name(io, mount_point, fd, &verity_blk_name) < 0) {
        ERROR("Couldn't get verity device number!\n");
        goto out;
    }

    if (load_verity_state(fstab, &params.mode) < 0) {
        /* if accessing or updating the state failed, switch to the default
         * safe mode. This makes sure the device won't end up in an endless
         * restart loop, and no corrupted data will be exposed to userspace
         * without a warning. */
        params.mode = VERITY_MODE_EIO;
    }

    if (!verity.table) {
        goto out;
    }

    params.table = strdup(verity.table);
    if (!params.table) {
        goto out;
    }

    // verify the signature on the table
    if (verify_verity_signature(verity) < 0) {
        if (params.mode == VERITY_MODE_LOGGING) {
            // the user has been warned, allow mounting without dm-verity
            retval = FS_MGR_SETUP_VERITY_SUCCESS;
            goto out;
        }

        // invalidate root hash and salt to trigger device-specific recovery
        if (invalidate_table(params.table, verity.table_length) < 0) {
            goto out;
        }
    }

    INFO("Enabling dm-verity for %s (mode %d)\n", mount_point, params.mode);

    if (fstab->fs_mgr_flags & MF_SLOTSELECT) {
        // Update the verity params using the actual block device path
        update_verity_table_blk_device(fstab->blk_device, &params.table);
    }
```

#### load_verity_table -> format_verity_table

```
    // load the verity mapping table
    if (load_verity_table(io, mount_point, verity.data_size, fd, &params,
            format_verity_table) == 0) {
        goto loaded;
    }

    if (params.ecc.valid) {
        // kernel may not support error correction, try without
        INFO("Disabling error correction for %s\n", mount_point);
        params.ecc.valid = false;

        if (load_verity_table(io, mount_point, verity.data_size, fd, &params,
                format_verity_table) == 0) {
            goto loaded;
        }
    }
```

#### load_verity_table -> format_legacy_verity_table

```
    // try the legacy format for backwards compatibility
    if (load_verity_table(io, mount_point, verity.data_size, fd, &params,
            format_legacy_verity_table) == 0) {
        goto loaded;
    }

    if (params.mode != VERITY_MODE_EIO) {
        // as a last resort, EIO mode should always be supported
        INFO("Falling back to EIO mode for %s\n", mount_point);
        params.mode = VERITY_MODE_EIO;

        if (load_verity_table(io, mount_point, verity.data_size, fd, &params,
                format_legacy_verity_table) == 0) {
            goto loaded;
        }
    }

    ERROR("Failed to load verity table for %s\n", mount_point);
    goto out;
```

#### resume_verity_table

```
loaded:

    // activate the device
    if (resume_verity_table(io, mount_point, fd) < 0) {
        goto out;
    }
```

#### fs_mgr_set_blk_ro

```
    // mark the underlying block device as read-only
    fs_mgr_set_blk_ro(fstab->blk_device);

    // assign the new verity block device as the block device
    free(fstab->blk_device);
    fstab->blk_device = verity_blk_name;
    verity_blk_name = 0;

    // make sure we've set everything up properly
    if (test_access(fstab->blk_device) < 0) {
        goto out;
    }

    retval = FS_MGR_SETUP_VERITY_SUCCESS;

out:
    if (fd != -1) {
        close(fd);
    }

    fec_close(f);
    free(params.table);
    free(verity_blk_name);

    return retval;
}
```

### load_verity_table

```
static int load_verity_table(struct dm_ioctl *io, char *name, uint64_t device_size, int fd,
        const struct verity_table_params *params, format_verity_table_func format)
{
    char *verity_params;
    char *buffer = (char*) io;
    size_t bufsize;

    verity_ioctl_init(io, name, DM_STATUS_TABLE_FLAG);

    struct dm_target_spec *tgt = (struct dm_target_spec *) &buffer[sizeof(struct dm_ioctl)];

    // set tgt arguments
    io->target_count = 1;
    tgt->status = 0;
    tgt->sector_start = 0;
    tgt->length = device_size / 512;
    strcpy(tgt->target_type, "verity");

    // build the verity params
    verity_params = buffer + sizeof(struct dm_ioctl) + sizeof(struct dm_target_spec);
    bufsize = DM_BUF_SIZE - (verity_params - buffer);

    if (!format(verity_params, bufsize, params)) {
        ERROR("Failed to format verity parameters\n");
        return -1;
    }

    INFO("loading verity table: '%s'", verity_params);

    // set next target boundary
    verity_params += strlen(verity_params) + 1;
    verity_params = (char*)(((unsigned long)verity_params + 7) & ~8);
    tgt->next = verity_params - buffer;

    // send the ioctl to load the verity table
    if (ioctl(fd, DM_TABLE_LOAD, io)) {
        ERROR("Error loading verity table (%s)\n", strerror(errno));
        return -1;
    }

    return 0;
}
```

Kernel Implementation
----------------------------------------

path: include/uapi/linux/dm-ioctl.h
```
/*
 * A traditional ioctl interface for the device mapper.
 *
 * Each device can have two tables associated with it, an
 * 'active' table which is the one currently used by io passing
 * through the device, and an 'inactive' one which is a table
 * that is being prepared as a replacement for the 'active' one.
 *
 * DM_VERSION:
 * Just get the version information for the ioctl interface.
 *
 * DM_REMOVE_ALL:
 * Remove all dm devices, destroy all tables.  Only really used
 * for debug.
 *
 * DM_LIST_DEVICES:
 * Get a list of all the dm device names.
 *
 * DM_DEV_CREATE:
 * Create a new device, neither the 'active' or 'inactive' table
 * slots will be filled.  The device will be in suspended state
 * after creation, however any io to the device will get errored
 * since it will be out-of-bounds.
 *
 * DM_DEV_REMOVE:
 * Remove a device, destroy any tables.
 *
 * DM_DEV_RENAME:
 * Rename a device or set its uuid if none was previously supplied.
 *
 * DM_SUSPEND:
 * This performs both suspend and resume, depending which flag is
 * passed in.
 * Suspend: This command will not return until all pending io to
 * the device has completed.  Further io will be deferred until
 * the device is resumed.
 * Resume: It is no longer an error to issue this command on an
 * unsuspended device.  If a table is present in the 'inactive'
 * slot, it will be moved to the active slot, then the old table
 * from the active slot will be _destroyed_.  Finally the device
 * is resumed.
 *
 * DM_DEV_STATUS:
 * Retrieves the status for the table in the 'active' slot.
 *
 * DM_DEV_WAIT:
 * Wait for a significant event to occur to the device.  This
 * could either be caused by an event triggered by one of the
 * targets of the table in the 'active' slot, or a table change.
 *
 * DM_TABLE_LOAD:
 * Load a table into the 'inactive' slot for the device.  The
 * device does _not_ need to be suspended prior to this command.
 *
 * DM_TABLE_CLEAR:
 * Destroy any table in the 'inactive' slot (ie. abort).
 *
 * DM_TABLE_DEPS:
 * Return a set of device dependencies for the 'active' table.
 *
 * DM_TABLE_STATUS:
 * Return the targets status for the 'active' table.
 *
 * DM_TARGET_MSG:
 * Pass a message string to the target at a specific offset of a device.
 *
 * DM_DEV_SET_GEOMETRY:
 * Set the geometry of a device by passing in a string in this format:
 *
 * "cylinders heads sectors_per_track start_sector"
 *
 * Beware that CHS geometry is nearly obsolete and only provided
 * for compatibility with dm devices that can be booted by a PC
 * BIOS.  See struct hd_geometry for range limits.  Also note that
 * the geometry is erased if the device size changes.
 */
```