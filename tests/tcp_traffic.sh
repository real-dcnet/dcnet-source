#!/bin/bash

echo "TCP bandwidth from host $1 to host $2" >> $3
ssh -t dcnet@$2 "iperf3 -s -1" >> "/dev/null" &
ssh -t dcnet@$1 "iperf3 -t 25 -c $2" >> $3 &
