#!/bin/bash

TEST_DIRS=("lf5-lf5" "lf5-lf6" "lf5-lf3" "lf5-lf8" "lf5-lf14")

for dir in $TEST_DIRS
do
	for i in $(seq 1 10)
	do
		scp "dcnet@128.10.126.57:$dir/run$i/migrate_test_vm.out" "$1/$dir/run$i"
	done
done
