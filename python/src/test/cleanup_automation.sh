#!/bin/bash

sec=$1
err=$2
sleep $sec
automate_pid=$(ps aux | grep automate_ping_tests.sh | grep -iv "grep" | awk '{ print $2 }')
python_pid=$(ps aux | grep "folded_clos" | sed -n '2 p' | awk '{ print $2 }')
pid=$(ps aux | grep -i "onos" | grep -i "/usr/bin/java" | grep -iv "grep" | awk '{ print $2 }' | head -n 1)

echo "Executing Automation Cleanup Process"
if [[ ! -z $pid ]]
then
    echo "killing onos controller"
    sudo kill -9 $pid
fi
if [[ ! -z $automate_pid ]]
then
    echo "Killing automation script"
    sudo kill -9 $automate_pid
fi
if [[ ! -z $python_pid ]]
then
    echo "Killing mininet topology"
    sudo kill -9 $python_pid
    sudo mn -c
fi

if [[ err -eq 0 ]] && [[ -d ~/onos ]] && [[ -e ~/onos-2.1.0.tar.gz ]] && [[ $(gzip -q -t ~/onos-2.1.0.tar.gz) -eq 0 ]]
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
