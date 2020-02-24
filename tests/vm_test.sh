#!/bin/bash

mkdir "run$1"
ssh -o StrictHostKeyChecking=no -t dcnet@128.10.126.57 "ping -c 100 128.10.126.65" > "run$1/migrate_test_vm.out" &
ping -c 100 128.10.126.57 > "run$1/migrate_test_hv.out"
