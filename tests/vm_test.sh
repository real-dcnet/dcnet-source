#!/bin/bash

# Arg 1: QEMU management connection source
# Arg 2: QEMU management connection destination
# Arg 3: Migration connection source
# Arg 4: Migration connection source
# Arg 5: Directory to store runs

count=80

for i in $(seq 1 10)
do
	VM_FILE="$5/run$i/migrate_test_vm.out"
	HV_FILE="$5/run$i/migrate_test_hv.out"
	ssh -o StrictHostKeyChecking=no dcnet@128.10.126.57 "mkdir -p $5/run$i"
	sleep 1
	ssh -o StrictHostKeyChecking=no dcnet@128.10.126.57 \
		"echo \"--- Ping during migration from $1 to $2, address $4 ---\" > $VM_FILE"
	sleep 1
	ssh -o StrictHostKeyChecking=no dcnet@128.10.126.57 "ping -c $count $3 >> $VM_FILE &"
	ssh -o StrictHostKeyChecking=no dcnet@$1 "ping -c $count $4 > /dev/null &"
	ssh -o StrictHostKeyChecking=no dcnet@$2 "ping -c $count $3 > /dev/null &"
	mkdir -p "$5/run$i"
	echo "--- Ping during migration from $1 to $2, address $4 ---" > $HV_FILE
	ping -c $count 128.10.126.57 >> $HV_FILE &
	sleep 20
	echo "Migration Emminent"
	source migrate_vm.sh $1 $2 $4 "128.10.126.57"
	sleep $(($count - 20))


	ssh -o StrictHostKeyChecking=no dcnet@128.10.126.57 "echo \"\" >> $VM_FILE"
	sleep 1
	ssh -o StrictHostKeyChecking=no dcnet@128.10.126.57 \
		"echo \"--- Ping during migration from $2 to $1, address $3 ---\" >> $VM_FILE"
	sleep 1
	ssh -o StrictHostKeyChecking=no dcnet@128.10.126.57 "ping -c $count $3 >> $VM_FILE &"
	echo "" >> $HV_FILE 
	echo "--- Ping during migration from $2 to $1, address $3 ---" >> $HV_FILE
	ssh -o StrictHostKeyChecking=no dcnet@$1 "ping -c $count $4 > /dev/null &"
	ssh -o StrictHostKeyChecking=no dcnet@$2 "ping -c $count $3 > /dev/null &"
	ping -c $count 128.10.126.57 >> $HV_FILE &
	sleep 20
	echo "Migration Emminent"
	source migrate_vm.sh $2 $1 $3 "128.10.126.57"
	sleep $(($count - 20))
done
