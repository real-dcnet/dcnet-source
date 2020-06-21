#!/bin/bash

# Arg 1: QEMU management connection source
# Arg 2: QEMU management connection destination
# Arg 3: Migration connection destination
# Arg 4: VM Address

virsh -c qemu+ssh://dcnet@$1/system migrate-setspeed ubuntu 500
virsh -c qemu+ssh://dcnet@$1/system migrate ubuntu qemu+ssh://dcnet@$2/system tcp://$3 --live --verbose

# Uncomment this line for DCnet migration
#echo "$3/32:$4/32:" > /dev/udp/10.0.1.8/10018

# Uncomment this line for reactive forwarding migration
echo "reactive:$4/32:" > /dev/udp/10.0.1.8/10018
