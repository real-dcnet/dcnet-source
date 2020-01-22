#!/bin/bash

echo "Initial ping from host $1 to host $2" >> $3
ssh -t dcnet@$1 "ping -c 3 $2" >> $3
sleep 2
echo "Steady-state ping from host $1 to host $2" >> $3
ssh -t dcnet@$1 "ping -c 20 $2" >> $3
