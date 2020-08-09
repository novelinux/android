#!/bin/sh
# bootchart logger installation
#

ROOT=/

# Remove the bootchartd files
rm -f $ROOT/sbin/bootchartd
rm -f $ROOT/etc/bootchartd.conf

# Remove the grub/lilo entry
if [ -x /sbin/grubby ]; then
	title="Bootchart logging"
	grubby --remove-kernel TITLE="$title"
fi
