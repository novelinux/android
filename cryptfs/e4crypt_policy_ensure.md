e4crypt_policy_ensure
========================================

path: system/extras/ext4_utils/ext4_crypt.cpp
```
int e4crypt_policy_ensure(const char *directory, const char *policy, size_t policy_length) {
    bool is_empty;
    if (!is_dir_empty(directory, &is_empty)) return -1;
    if (is_empty) {
        if (!e4crypt_policy_set(directory, policy, policy_length)) return -1;
    } else {
        if (!e4crypt_policy_check(directory, policy, policy_length)) return -1;
    }
    return 0;
}
```

struct ext4_encryption_policy
----------------------------------------

path: system/extras/ext4_utils/ext4_crypt.cpp
```
struct ext4_encryption_policy {
    char version;
    char contents_encryption_mode;
    char filenames_encryption_mode;
    char flags;
    char master_key_descriptor[EXT4_KEY_DESCRIPTOR_SIZE];
} __attribute__((__packed__));
```

e4crypt_policy_set
----------------------------------------

path: system/extras/ext4_utils/ext4_crypt.cpp
```
static bool e4crypt_policy_set(const char *directory, const char *policy, size_t policy_length) {
    if (policy_length != EXT4_KEY_DESCRIPTOR_SIZE) {
        LOG(ERROR) << "Policy wrong length: " << policy_length;
        return false;
    }
    int fd = open(directory, O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (fd == -1) {
        PLOG(ERROR) << "Failed to open directory " << directory;
        return false;
    }

    ext4_encryption_policy eep;
    eep.version = 0;
    eep.contents_encryption_mode = EXT4_ENCRYPTION_MODE_PRIVATE;
    eep.filenames_encryption_mode = EXT4_ENCRYPTION_MODE_AES_256_CTS;
    eep.flags = 0;
    memcpy(eep.master_key_descriptor, policy, EXT4_KEY_DESCRIPTOR_SIZE);
    if (ioctl(fd, EXT4_IOC_SET_ENCRYPTION_POLICY, &eep)) {
        PLOG(ERROR) << "Failed to set encryption policy for " << directory;
        close(fd);
        return false;
    }
    close(fd);

    char policy_hex[EXT4_KEY_DESCRIPTOR_SIZE_HEX];
    policy_to_hex(policy, policy_hex);
    LOG(INFO) << "Policy for " << directory << " set to " << policy_hex;
    return true;
}
```

ioctl
----------------------------------------

path: fs/ext4/ioctl.c
```
long ext4_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
	struct inode *inode = file_inode(filp);
	struct super_block *sb = inode->i_sb;
	struct ext4_inode_info *ei = EXT4_I(inode);
	unsigned int flags;

	ext4_debug("cmd = %u, arg = %lu\n", cmd, arg);

	switch (cmd) {
        ...
        case EXT4_IOC_SET_ENCRYPTION_POLICY: {
#ifdef CONFIG_EXT4_FS_ENCRYPTION
		struct ext4_encryption_policy policy;
		int err = 0;

		if (copy_from_user(&policy,
				   (struct ext4_encryption_policy __user *)arg,
				   sizeof(policy))) {
			err = -EFAULT;
			goto encryption_policy_out;
		}

		err = ext4_process_policy(&policy, inode);
encryption_policy_out:
		return err;
#else
		return -EOPNOTSUPP;
#endif
	}
        ...
        }
        ...
}
```

ext4_process_policy
----------------------------------------

path: fs/ext4/crypto_policy.c
```
int ext4_process_policy(const struct ext4_encryption_policy *policy,
			struct inode *inode)
{
	if (policy->version != 0)
		return -EINVAL;

	if (!ext4_inode_has_encryption_context(inode)) {
		if (!S_ISDIR(inode->i_mode))
			return -EINVAL;
		if (!ext4_empty_dir(inode))
			return -ENOTEMPTY;
		return ext4_create_encryption_context_from_policy(inode,
								  policy);
	}

	if (ext4_is_encryption_context_consistent_with_policy(inode, policy))
		return 0;

	printk(KERN_WARNING "%s: Policy inconsistent with encryption context\n",
	       __func__);
	return -EINVAL;
}
```

* ext4_inode_has_encryption_context

```
static int ext4_inode_has_encryption_context(struct inode *inode)
{
	int res = ext4_xattr_get(inode, EXT4_XATTR_INDEX_ENCRYPTION,
				 EXT4_XATTR_NAME_ENCRYPTION_CONTEXT, NULL, 0);
	return (res > 0);
}
```

* ext4_create_encryption_context_from_policy

```
static int ext4_create_encryption_context_from_policy(
	struct inode *inode, const struct ext4_encryption_policy *policy)
{
	struct ext4_encryption_context ctx;
	handle_t *handle;
	int res, res2;

	res = ext4_convert_inline_data(inode);
	if (res)
		return res;

	ctx.format = EXT4_ENCRYPTION_CONTEXT_FORMAT_V1;
	memcpy(ctx.master_key_descriptor, policy->master_key_descriptor,
	       EXT4_KEY_DESCRIPTOR_SIZE);
	if (!ext4_valid_contents_enc_mode(policy->contents_encryption_mode)) {
		printk(KERN_WARNING
		       "%s: Invalid contents encryption mode %d\n", __func__,
			policy->contents_encryption_mode);
		return -EINVAL;
	}
	if (!ext4_valid_filenames_enc_mode(policy->filenames_encryption_mode)) {
		printk(KERN_WARNING
		       "%s: Invalid filenames encryption mode %d\n", __func__,
			policy->filenames_encryption_mode);
		return -EINVAL;
	}
	if (policy->flags & ~EXT4_POLICY_FLAGS_VALID)
		return -EINVAL;
	ctx.contents_encryption_mode = policy->contents_encryption_mode;
	ctx.filenames_encryption_mode = policy->filenames_encryption_mode;
	ctx.flags = policy->flags;
	BUILD_BUG_ON(sizeof(ctx.nonce) != EXT4_KEY_DERIVATION_NONCE_SIZE);
	get_random_bytes(ctx.nonce, EXT4_KEY_DERIVATION_NONCE_SIZE);

	handle = ext4_journal_start(inode, EXT4_HT_MISC,
				    ext4_jbd2_credits_xattr(inode));
	if (IS_ERR(handle))
		return PTR_ERR(handle);
	res = ext4_xattr_set(inode, EXT4_XATTR_INDEX_ENCRYPTION,
			     EXT4_XATTR_NAME_ENCRYPTION_CONTEXT, &ctx,
			     sizeof(ctx), 0);
	if (!res) {
		ext4_set_inode_flag(inode, EXT4_INODE_ENCRYPT);
		res = ext4_mark_inode_dirty(handle, inode);
		if (res)
			EXT4_ERROR_INODE(inode, "Failed to mark inode dirty");
	}
	res2 = ext4_journal_stop(handle);
	if (!res)
		res = res2;
	return res;
}
```