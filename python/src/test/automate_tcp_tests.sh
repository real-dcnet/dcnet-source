#!/bin/bash

#Performs automated tcp tests on the mininet testbed
#@param dir: the directory for the first 5 throwaway tests
#@param realdir: The directory where the actual tests to be reported will be stored
#@param outFile: An output log file to monitor what's going on while you are away from the monitor
#@param maxtrials: The number of tests to perform for that new instance of onos

SECONDS=0
discards=1
realdir=$1
outFile=$2
maxtrials=$3
traffic=$4

if [[ -f $outFile ]]
then 
    rm -rf $outFile
fi
touch $outFile

pid=$(ps aux | grep -i "onos" | grep -i "/usr/bin/java" | grep -iv "grep" | awk '{ print $2 }' | head -n 1)

if [[ -z $pid ]]
then
    echo -e "Error. onos service isn't currently running."
    echo -e "Exiting."
    exit 0
fi

trials=1
while [[ $trials -le $maxtrials ]]
do
    echo "---------------------------Real Test $trials--------------------------------------------------------------------" 
    echo "---------------------------Real Test $trials--------------------------------------------------------------------" >> $outFile
    cd ..
    sudo python folded_clos.py --dc 3 --leaf 2 2 2 --spine 2 2 2 --pod 3 3 2 --ratio 2 2 1 --fanout 3 3 3 --test true --run_ping false --with_traffic $traffic
    cd test
    packetloss=$(grep -i "packet loss" tcp_test_no_load.out | grep -ivwc "0% packet loss")
    error=$(grep -i "iperf3: error" tcp_test_no_load.out)
    DUPS=$(grep -ic "DUP!" tcp_test_no_load.out)
    realsubdir="/run${trials}"
    pingdir=$realdir$realsubdir
    if [[ $error != "" ]]
    then
	echo "Trial $trials unsuccessful. An error occurred."
    elif [[ $packetloss -eq 0 ]] && [[ $DUPS -eq 0 ]]
    then
        echo "Trial $trials successful"
        if [[ ! -d $pingdir ]]
        then 
            mkdir -p $pingdir
        fi
        mv -vf tcp_test_no_load.out $pingdir
        echo -e "----------------------End of Real Test $trials---------------------------------------------------------------\n"
        echo -e "----------------------End of Real Test $trials---------------------------------------------------------------\n" >> $outFile
        trials=$(( $trials+1 ))
        sleep 2
    elif [[ $DUPS -ne 0 ]]
    then
        echo "Duplicate packets found in ${pingdir}"
        dups="${realdir}dups/run${trials}"
        if [[ ! -d $dups ]]
        then
            mkdir -p $dups
        fi
        mv -vf tcp_test_no_load.out $dups
    else
        acceptable=0
        while [[ acceptable -lt 5 ]] && [[ $packetloss -ne 0 ]]
        do
            echo "Attempt $(( $acceptable+1 )): Retrying Trial $trial to correct for packet error"
            echo "Attempt $(( $acceptable+1 )): Retrying Trial $trial to correct for packet error" >> $outFile
            cd ..
            sudo python folded_clos.py --dc 3 --leaf 2 2 2 --spine 2 2 2 --pod 3 3 2 --ratio 2 2 1 --fanout 3 3 3 --test true --run_ping false --with_traffic $traffic
            cd test
            packetloss=$(grep -i "packet loss" tcp_test_no_load.out | grep -ivwc "0% packet loss")
            if [[ $packetloss -eq 0 ]]
            then
                echo "Attempt $(( $acceptable+1 )): Trial $trial successful."
                echo "Attempt $(( $acceptable+1 )): Trial $trial successful." >> $outFile
                if [[ ! -d $pingdir ]]
                then 
                    mkdir -p $pingdir
                fi
                mv -vf tcp_test_no_load.out $pingdir
                trials=$(( $trials+1 ))
                echo -e "\n"
                echo -e "\n" >> $outFile
                sleep 2
            fi
            acceptable=$(( $acceptable +1 ))
        done
        if [[ $acceptable -eq 5 ]]
        then
            echo "Trial $trial unsuccessful: Huge Packet loss frequent."
            echo "Trial $trial unsuccessful: Huge Packet loss frequent." >> $outFile
            pid=$(ps aux | grep -i "onos" | grep -iv "grep" | awk '{ print $2 }' | head -n 1)
            ./cleanup_automation.sh 1 1 &
            exit
        fi
    fi
done
        

pid=$(ps aux | grep -i "onos" | grep -i "/usr/bin/java" | grep -iv "grep" | awk '{ print $2 }' | head -n 1)
if [[ ! -z $pid ]]
then
    echo "Killing Onos Controller"
    sudo kill -9 $pid
fi

echo "Automation successful."
echo "Automation successful." >> $outFile

if (( $SECONDS > 3600 )) ; then
    let "hours=SECONDS/3600"
    let "minutes=(SECONDS%3600)/60"
    let "seconds=(SECONDS%3600)%60"
    echo "Completed in $hours hour(s), $minutes minute(s) and $seconds second(s)" 
elif (( $SECONDS > 60 )) ; then
    let "minutes=(SECONDS%3600)/60"
    let "seconds=(SECONDS%3600)%60"
    echo "Completed in $minutes minute(s) and $seconds second(s)"
else
    echo "Completed in $SECONDS seconds"
fi
