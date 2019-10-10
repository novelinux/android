# Kernel Implementation

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