#!/bin/bash

echo "--- TCP bandwidth from host $1 to host $2 ---" >> $3
ssh -o StrictHostKeyChecking=no -t dcnet@$2 "iperf3 -s -1" >> "/dev/null" &
sleep 2
ssh -o StrictHostKeyChecking=no -t dcnet@$1 "iperf3 -t 50 -c $2" >> $3
echo "" >> $3
sleep 25
