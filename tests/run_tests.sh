#!/bin/bash

SRC_IP="128.10.135.77"

DST_IPS=("128.10.135.85" "128.10.135.69" "128.10.135.75" "128.10.135.76" "128.10.135.78")
OTHER_IPS=()
for ip in $(cat ~/host_info.txt | awk '{print $1}')
do
	if [ $ip != $SRC_IP ] && [ $(echo ${DST_IPS[@]} | grep -c $ip) -eq 0 ]
	then
		OTHER_IPS+=($ip)
	fi
done

echo > out.txt
for i in $(seq 1 10)
do
	for j in ${!DST_IPS[@]}
	do
		source "./ping_test.sh" "$SRC_IP" "${DST_IPS[$j]}" "out.txt"
		TRAFFIC=($(shuf -e ${OTHER_IPS[@]}))
		for k in $(seq 1 $(expr ${#TRAFFIC[@]} / 2 - 1))
		do
			source "./tcp_traffic.sh" "${TRAFFIC[(2 * $j)]}" "${TRAFFIC[(2 * $j + 1)]}" "/dev/null"
			source "./tcp_test.sh" "$SRC_IP" "${DST_IPS[$j]}" "out.txt"
		done
	done
done
