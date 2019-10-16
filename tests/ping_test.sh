#!/bin/bash

echo "Initial ping from host $1 to host $2" >> $4
sshpass -p $3 ssh -t root@$1 "ping -c 1 $2" >> $4
sleep 5
echo "Steady-state ping from host $1 to host $2" >> $4
sshpass -p $3 ssh -t root@$1 "ping -c 100 $2" >> $4
