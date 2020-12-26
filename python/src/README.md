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

## onos cli
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

### Helpful Commands
apps -a -s to list currently running applications  
apps -s to list all installed applications  
help onos  
flows  
links  
hosts  

Information about these commands can be found https://wiki.onosproject.org/display/ONOS/Appendix+A+%3A+CLI+commands

Additional information about some of these commands can be found https://wiki.onosproject.org/display/ONOS/Basic+ONOS+Tutorial

## Scripts
The automate_ping_tests.sh script is used to perform the ping tests for either dcnet or reactive forwarding automatically. Depending on the number of trials that you need to run and the host machine's system resources the script may take several hours to complete. 

check_outliers.py is to detect outliers that are present in the ping tests results. Once the outliers are found, script will output all the files that contains outliers

### Running the automation scripts
To start the automation process go to the test directory and run the command:
```
cd test
./automate_ping_tests.sh <discard directory> <test data directory> <logfile> <max test trials>
```
The ```<discard directory>``` is the directory where you want to place ping test data (i.e. dcnet or reactive forwarding) that would be discarded. By default the automate_ping_tests.sh will discard the first 5 ping tests it successfully runs. 

The <test data directory> is the directory where you want to put ping test data (i.e. dcnet or reactive forwarding) that will be used for plotting. 

The <logfile> outputs information about the automate script performed. For example, whether it found duplicate packets or detected packet loss. Or attempting to correct for these issues by rerunning the ping tests. 

The <max test trials> is number of trials that should be run for ping test data. 

The automate_ping_tests.sh returns the time taken to complete the script.

The automate_ping_tests.sh script uses the check_outliers.py script to find files that contain outliers in <test data directory>. Once outliers are detected, the automate_ping_tests.sh will rerun the tests that contained the outliers until all outliers are eliminated. 

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


