#!/bin/bash

sec=$1
sleep $sec
automate_pid=$(ps aux | grep automate_ping_tests.sh | grep -iv "grep" | awk '{ print $2 }')
python_pid=$(ps aux | grep "folded_clos" | sed -n '2 p' | awk '{ print $2 }')
pid=$(ps aux | grep -i "onos" | grep -iv "grep" | awk '{ print $2 }' | head -n 1)

echo "Executing Automation Cleanup Process"
if [[ ! -z $pid ]]
then
    sudo kill -9 $pid
fi
if [[ ! -z $automate_pid ]]
then
    sudo kill -9 $automate_pid
fi
if [[ ! -z $python_pid ]]
then
    sudo kill -9 $python_pid
    sudo mn -c
fi

if [[ -d ~/onos ]]
then
    cd ~
    rm -rf onos
    tar -xpzf onos-2.1.0.tar.gz
    mv onos-2.1.0 onos
    cd - 
    ssh-keygen -f "$HOME/.ssh/known_hosts" -R "[localhost]:8101"
fi

echo "Done with cleanup"
exit
