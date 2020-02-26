#!/bin/bash

SRC_QEMU="128.10.126.58"
SRC_IP="128.10.126.61"

DST_QEMU=("128.10.126.66" "128.10.126.74" "128.10.126.50" "128.10.126.82" "128.10.126.90")
DST_IPS=("128.10.126.69" "128.10.126.77" "128.10.126.51" "128.10.126.84" "128.10.126.95")
TEST_DIRS=("lf5-lf5" "lf5-lf6" "lf5-lf3" "lf5-lf8" "lf5-lf14")

for i in ${!DST_IPS[@]}
do
	echo "Tests for ${TEST_DIRS[$i]}"
	source "./vm_test.sh" "$SRC_QEMU" "${DST_QEMU[$i]}" "$SRC_IP" "${DST_IPS[$i]}" "${TEST_DIRS[$i]}"
done
