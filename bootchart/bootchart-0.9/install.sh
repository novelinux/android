#!/bin/sh
#
# bootchart logger installation
#

# $ROOT must be mounted during sysinit startup
ROOT=/

# Install the bootchartd files
install -m 755 script/bootchartd $ROOT/sbin/bootchartd
install -m 755 script/bootchartd.conf $ROOT/etc/bootchartd.conf

# Add a new grub/lilo entry
if [ -x /sbin/grubby ]; then	
	kernel=$(grubby --default-kernel)
	initrd=$(grubby --info=$kernel | sed -n '/^initrd=/{s/^initrd=//p;q;}')
	[ ! -z $initrd ] && initrd="--initrd=$initrd"
	title="Bootchart logging"
	grubby --remove-kernel TITLE="$title"
	grubby --copy-default --add-kernel=$kernel $initrd --args="init=/sbin/bootchartd" --title="$title"
fi
