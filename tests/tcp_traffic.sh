#!/bin/bash

echo "TCP bandwidth from host $1 to host $2" >> $5
sshpass -p $3 ssh -t root@$2 "iperf3 -s &" >> $5
sshpass -p $4 ssh -t root@$1 "iperf3 -t 105 -c $2 &" >> $5
