#!/bin/bash

input="host_tcp.txt"

for i in $(seq 1 20)
do
	echo "Test Run $i"
	mkdir run$i
	while IFS= read -r line
	do
		loc=$(echo $line | awk '{print $1}')
		src=$(echo $line | awk '{print $2}')
		dst=$(echo $line | awk '{print $3}')
		echo "Testing bandwidth from $SRC_IP to ${DST_IPS[$j]}"
		source "./tcp_test.sh" "$src" "$dst" "run$i/"$loc"_tcp.out"
	done < "$input"
done
