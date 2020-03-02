#!/bin/bash

echo "TCP bandwidth from host $1 to host $2" >> $4
ssh -o StrictHostKeyChecking=no dcnet@$2 "iperf3 -s -p $3 -1" >> "/dev/null" &
sleep 2
ssh -o StrictHostKeyChecking=no dcnet@$1 "iperf3 -t 60 -c $2 -p $3" >> $4
