#!/bin/bash

# Arg 1: QEMU management connection source
# Arg 2: QEMU management connection destination
# Arg 3: Migration connection destination

sleep 20
virsh -c qemu+ssh://dcnet@$1/system migrate ubuntu qemu+ssh://dcnet@$2/system tcp://$3 --live --verbose
