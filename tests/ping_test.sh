#!/bin/bash

echo "Initial ping from host $1 to host $2" >> $3
ssh -t dcnet@$1 "ping -c 1 $2" >> $3
sleep 5
echo "Steady-state ping from host $1 to host $2" >> $3
ssh -t dcnet@$1 "ping -c 100 $2" >> $3
