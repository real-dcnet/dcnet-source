# Testing
The files present in this directory are needed to run the dcnet application smoothly

## Data
The directories dcnet-basic and reactive-basic contains test data from the tcp tests and ping tests for the dcnet forwarding and reactive forwarding

## Dependencies
The following commmands is needed to run the check_outliers.py script
```
sudo apt install python3 python3-pip 
pip3 install scipy
```

## ONOS CLI
```
ssh -p 8101  karaf@localhost
password: karaf
enter the command "onos" to bring up the onos cli
app activate org.onosproject.openflow
# activates reactive forwarding, this is required
app activate fwd 

# required if you are testing dcnet protocol. 
# run the command after you install dcnet 
app activate dcnet 
```

### Helpful ONOS CLI Commands
`apps -a -s` to list currently running applications  
`apps -s` to list all installed applications  
`help onos` 
`flows`  
`links`  
`hosts`

Information about these commands can be found https://wiki.onosproject.org/display/ONOS/Appendix+A+%3A+CLI+commands

Additional information about some of these commands can be found https://wiki.onosproject.org/display/ONOS/Basic+ONOS+Tutorial

## Scripts
The automate_ping_tests.sh is a helper script that automatically runs the ping tests for either dcnet or reactive forwarding automatically. The first 5 results are discarded because of inexpliccable issues with extraneous delays. Depending on the number of trials that you need to run and the host machine's system resources the script may take several hours to complete. It uses the check_outliers.py script to look for outliers present in the ```<test data directory>``` and then removes them until none can be found and the cleanup_automation to terminate onos and mininet processes if it notices consistent pakcet loss or high amount of dupicate packets recurring in a test. 

check_outliers.py is to detect outliers that are present in the ping tests results. Occassionally, there may be instances where are protracted delays, oftentimes unaccountable, in the initial ping from the ping tests. Once the outliers are found, script will output all the files that contains outliers. check_outliers was developed with python3 only. 

cleanup_automation.sh terminates all extant processes relating to ONOS and mininet. This script is primarily used by the automate_ping_tests.sh script if it detects problems with onos, however can be used by the user to kill the onos service, mininet processes and/or autmoate_ping_tests.sh process. 

### Running the automation scripts
To start the automation process go to the test directory and run the command:
```
cd test
./automate_ping_tests.sh <discard directory> <test data directory> <logfile> <max test trials>
```
The ```<discard directory>``` is the directory where you want to place ping test data (i.e. dcnet or reactive forwarding) that would be discarded. By default the automate_ping_tests.sh will discard the first 5 ping tests it successfully runs. 

The ```<test data directory>``` is the directory where you want to put ping test data (i.e. dcnet or reactive forwarding) that will be used for plotting. 

The ```<logfile>``` outputs information about the automate script performed. For example, whether it found duplicate packets or detected packet loss. Or attempting to correct for these issues by rerunning the ping tests. 

The ```<max test trials>``` is number of trials that should be run for ping test data. 

The automate_ping_tests.sh returns the time taken to complete the script.

The automate_ping_tests.sh script uses the check_outliers.py script to find files that contain outliers in ```<test data directory>```. Once outliers are detected, the automate_ping_tests.sh will rerun the tests that contained the outliers until all outliers are eliminated. 

To run the cleanup_automation.sh go to the test directory and run the command:
```
cd test
./cleanup_automation.sh <sec> <err>
```
The ```<sec>``` is the amount of seconds that cleanup_automation.sh script should wait before executing. The value is set to 1 when used by the autmoate_ping_tests.sh script but can be set to 0 when run by the end user. 

The ```<err>``` is a binary value of 0 or nonzero and is used to automate the process of deleting onos directory and then extracting it from the tarball. If the value is 0, onos directory will be deleted and then extracted from the onos.tar.gz file. This assumes that the onos directory is named onos and is directory in the the user's home directory. The parameter is used to reinstall onos if problems persist while using onos.

To run the check_outliers.py go to the test directory and run the command:
```
cd test
python3 check_outliers.py <test data directory> <max test trials>
```
The ```<test data directory>``` is the directory where your ping test data (i.e. dcnet or reactive forwarding) is located. 
The ```<max test trials>``` is number of trials that you want to check in ```<test data directory>```. 

# Plotting

The plot_ping.py plots the ping tests results.
To run it, navigate to the src directory and run the command 
```
python2 plot_ping.py <ping data test directory> <graph title>
```

The plot_tcp.py plots the tcp tests results.
To run it, navigate to the src directory and run the command 
```
python2 plot_tcp.py <tcp data test directory> <graph title>
```

The plot_init_ping_comp.py plots the comparison between the dcnet tests results and reactive-forwarding tests results for initial ping
To run it, navigate to the src directory and run the command 
```
python2 plot_init_ping_comp.py <dcnet ping test directory> <fwd ping test directory > <graph title>
```

The plot_steady_ping_comp.py plots the comparison between the dcnet tests results and reactive-forwarding tests results for steady ping
To run it, navigate to the src directory and run the command 
```
python2 plot_steady_ping_comp.py <dcnet ping test directory> <fwd ping test directory > <graph title>
```


