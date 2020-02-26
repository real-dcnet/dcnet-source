#!/bin/bash

echo "TCP bandwidth from host $1 to host $2" >> $3
ssh -o StrictHostKeyChecking=no -t dcnet@$2 "iperf3 -s -p $4 -1" >> "/dev/null" &
sleep 2
ssh -o StrictHostKeyChecking=no -t dcnet@$1 "iperf3 -t 60 -c $2 -p $4" >> $3 &
