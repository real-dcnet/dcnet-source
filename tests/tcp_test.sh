#!/bin/bash

echo "TCP bandwidth from host $1 to host $2" >> $3
ssh -t root@$2 "iperf3 -s" >> $3
ssh -t root@$1 "iperf3 -t 100 -c $2" >> $3
sleep 5
