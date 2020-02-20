#!/bin/bash

echo "--- Initial ping from host $1 to host $2 ---" >> $3
ssh -o StrictHostKeyChecking=no -t dcnet@$1 "ping -c 5 $2" >> $3
echo "" >> $3
sleep 2
echo "--- Steady-state ping from host $1 to host $2 ---" >> $3
ssh -o StrictHostKeyChecking=no -t dcnet@$1 "ping -c 50 $2" >> $3
echo "" >> $3
