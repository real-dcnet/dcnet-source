#!/bin/bash

discards=1
dir="discards/dcnet/"
while [[ $discards -le 5 ]]
do
    cd ..
    sudo python folded_clos.py --dc 3 --leaf 2 2 2 --spine 2 2 2 --pod 3 3 2 --ratio 2 2 1 --fanout 3 3 3 --test true
    cd test
    PACKETS=$(grep -i "packet loss" ping_test_no_load.out | grep -ivc "0% packet loss")
    DUPS=$(grep -ic "dup" ping_test_no_load.out)
    if [[ $PACKETS -eq 0 ]] && [[ $DUPS -eq 0 ]]
    then
        subdir="run${discards}"
        echo "Fake Trial $discards successfull"
        discards=$(( $discards+1 ))
        mv -vf ping_test_no_load.out $dir$subdir
    else
        echo "Fake Trial $discards not successful. Trying again"
    fi
done

trials=1
realdir="dcnet-basic/"
while [[ $trials -le 25 ]]
do
    cd ..
    sudo python folded_clos.py --dc 3 --leaf 2 2 2 --spine 2 2 2 --pod 3 3 2 --ratio 2 2 1 --fanout 3 3 3 --test true
    cd test
    PACKETS=$(grep -i "packet loss" ping_test_no_load.out | grep -ivc "0% packet loss")
    DUPS=$(grep -ic "dup" ping_test_no_load.out)
    if [[ $PACKETS -eq 0 ]] && [[ $DUPS -eq 0 ]]
    then
        grep -i -B 10 -A 1 "5 packets transmitted, 5 received" ping_test_no_load.out | cat > ping_init_no_load.out
        less ping_init_no_load.out
        grep -i -A 1 "50 packets transmitted, 50 received" ping_test_no_load.out | cat > ping_steady_no_load.out
        less ping_steady_no_load.out
        echo "Continue 1 for Yes and 0 for No: "
        read Con
        if [[ $Con -eq 1 ]]
        then
            realsubdir="run${trials}"
            echo "Trial $trials successfull"
            mv -vf ping_test_no_load.out $realdir$realsubdir
            trials=$(( $trials+1 ))
        else
            echo "Should I exit (0): "
            read decision
            if [[ $decision -eq 0 ]]
            then
                exit
            fi
        fi
    else
        echo "Packet duplicates or Huge Packet loss frequent"
        exit
    fi
done
        
        



