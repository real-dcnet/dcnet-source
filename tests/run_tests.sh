#!/bin/bash

SRC_IP="128.10.126.61"

DST_IPS=("128.10.126.69" "128.10.126.77" "128.10.126.51" "128.10.126.84" "128.10.126.95")
OTHER_IPS=()
for ip in $(cat host_info.txt | awk '{print $1}')
do
	if [ $ip != $SRC_IP ] && [ $(echo ${DST_IPS[@]} | grep -c $ip) -eq 0 ]
	then
		OTHER_IPS+=($ip)
	fi
done

for i in $(seq 1 20)
do
	echo "Test Run $i"
	mkdir run$i
	echo "" > "run$i/ping_test.out" 
	echo "" > "run$i/tcp_test.out" 
	for j in ${!DST_IPS[@]}
	do
		echo "Pinging ${DST_IPS[$j]} from $SRC_IP"
		source "./ping_test.sh" "$SRC_IP" "${DST_IPS[$j]}" "run$i/ping_test.out"
		TRAFFIC=($(shuf -e ${OTHER_IPS[@]}))
		for k in $(seq 1 $(expr ${#TRAFFIC[@]} / 2 - 1))
		do
			echo "${TRAFFIC[(2 * $k)]} connect to ${TRAFFIC[(2 * $k + 1)]}" 
			source "./tcp_traffic.sh" "${TRAFFIC[(2 * $j)]}" "${TRAFFIC[(2 * $j + 1)]}" "/dev/null"
		done
		#for k in $(seq 1 19)
		#do
		#	source "./tcp_traffic.sh" "$SRC_IP" "${DST_IPS[$j]}" "/dev/null" $((5210 + $k))
		#done
		echo "Testing bandwidth from $SRC_IP to ${DST_IPS[$j]}"
		source "./tcp_test.sh" "$SRC_IP" "${DST_IPS[$j]}" "run$i/tcp_test.out"
	done
done
