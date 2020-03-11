#!/bin/bash

input="host_tcp.txt"

for i in $(seq 1 20)
do
	echo "Test Run $i"
	mkdir run$i
	while IFS= read -r line
	do
		echo $line
		loc=$(echo $line | awk '{print $1}')
		echo $loc
		#echo "Testing bandwidth from $SRC_IP to ${DST_IPS[$j]}"
		#source "./tcp_test.sh" "$SRC_IP" "${DST_IPS[$j]}" "run$i/tcp_test.out"
	done < "$input"
done
