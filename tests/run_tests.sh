#!/bin/bash

SRC_IP="128.10.135.60"
SRC_PASS=$(cat ~/host_info.txt | grep $SRC_IP | awk '{print $2}')

DST_IPS=("128.10.135.61" "128.10.135.62" "128.10.135.63" "128.10.135.64" "128.10.135.65")
DST_PASSES=()
for ip in ${DST_IPS[@]}
do
	DST_PASSES+=($(cat ~/host_info.txt | grep $ip | awk '{print $2}'))
done

echo ${DST_IPS[@]}
OTHER_IPS=()
for ip in $(cat ~/host_info.txt | awk '{print $1}')
do
	if [ $ip != $SRC_IP ] && [ $(echo ${DST_IPS[@]} | grep -c $ip) -eq 0 ]
	then
		OTHER_IPS+=($ip)
	fi
done

echo ${OTHER_IPS[@]}
echo > out.txt
for i in $(seq 1 10)
do
	for j in ${!DST_IPS[@]}
	do
		source "./ping_test.sh" "$SRC_IP" "${DST_IPS[$j]}" "$SRC_PASS" "out.txt"
		TRAFFIC=($(shuf -e ${OTHER_IPS[@]}))
		for k in $(seq 1 $(expr ${#TRAFFIC[@]} / 2 - 1))
		do
			PASS1=$(cat ~/host_info.txt | grep ${TRAFFIC[(2 * $k)]} | awk '{print $2}')
			PASS2=$(cat ~/host_info.txt | grep ${TRAFFIC[(2 * $k + 1)]} | awk '{print $2}')
			source "./tcp_traffic.sh" "${TRAFFIC[(2 * $j)]}" "${TRAFFIC[(2 * $j + 1)]}" "$PASS1" "$PASS2" "/dev/null"
			source "./tcp_test.sh" "$SRC_IP" "${DST_IPS[$j]}" "$SRC_PASS" "${DST_PASSES[$j]}" "out.txt"
		done
	done
done
