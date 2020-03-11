#!/bin/bash

SRC_IP="128.10.126.61"

DST_IPS=("128.10.126.69" "128.10.126.77" "128.10.126.51" "128.10.126.84" "128.10.126.95")

for i in $(seq 1 20)
do
	echo "Test Run $i"
	mkdir run$i
	echo "" > "run$i/ping_test.out" 
	for j in ${!DST_IPS[@]}
	do
		echo "Pinging ${DST_IPS[$j]} from $SRC_IP"
		source "./ping_test.sh" "$SRC_IP" "${DST_IPS[$j]}" "run$i/ping_test.out"
	done
done
