#!/bin/bash

# Arg 1: QEMU management connection source
# Arg 2: QEMU management connection destination
# Arg 3: Migration connection destination
# Arg 4: VM Address

echo "$2:$4:" > /dev/udp/10.0.1.8/10018
virsh -c qemu+ssh://dcnet@$1/system migrate-setspeed ubuntu 500
virsh -c qemu+ssh://dcnet@$1/system migrate ubuntu qemu+ssh://dcnet@$2/system tcp://$3 --live --verbose
