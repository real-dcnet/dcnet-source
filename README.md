# DCnet
## Dependencies
The following commands install all dependencies required to run Mininet and the ONOS controller:
```
sudo apt install mininet
sudo apt install default-jdk
sudo apt install maven
wget https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.1.0/onos-2.1.0.tar.gz
tar -xvf onos-2.1.0.tar.gz
```

## Generating Mininet Topology
The following command generates the folded Clos topology in Mininet according to DCnet specifications and creates configuration files to use with the ONOS controller:
```
sudo python folded_clos.py  [--leaf NUM1 [NUM2 NUM3 ...]]
                            [--spine NUM1 [NUM2 NUM3 ...]]
                            [--pod NUM1 [NUM2 NUM3 ...]]
                            [--ratio NUM1 [NUM2 NUM3 ...]]
                            [--fanout NUM1 [NUM2 NUM3 ...]]
                            [--remote 0/1 [0/1 0/1 ...]]
                            [--dc NUM]
                            [--test]
```
All arguments are optional, with the default values and effects being:


--leaf   (Default 4) : Number of leaves in a pod

--spine  (Default 2) : Number of spines in a pod

--pod    (Default 4) : Number of pods in a data center

--ratio  (Default 2) : Number of super spines per spine

--fanout (Default 3) : Number of hosts per leaf

--dc     (Default 2) : Number of data centers in topology

--remote (Default 0) : Indicates if data centers are located remotely (higher latency)

--test   (Default 0) : Indicates if ping and TCP testing should be performed

Also note that every flag can take several arguments, up to the value for --dc. This gives different configuration settings for each data center. Single arguments will give the same configuration setting for all data centers. An argument count that is not equal to the number --dc is invalid and will default to using the first argument in the single argument action.

Running this command starts the Mininet CLI and creates three configuration files: switch_config.json, host_config.json, and top_config.json, which are placed in config/mininet for use by the ONOS controller.

## Running ONOS Controller
To start ONOS, go to the directory containing onos-2.1.0 that was extracted from the tarball and use the commands:
```
cd onos-2.1.0/bin
./onos-service
```

This starts the service that listens for a connection from Mininet. Then, access the web gui through http://localhost:8181/onos/ui, and use credentials:
```
Username: onos
Password: rocks
```

Once logged in to the gui, enable OpenFlow and reactive forwarding on the applications tab.

## Installing DCnet Application
To start the DCnet application for ONOS, change directory to dcnet-source/dcnet, and build the app using:
```
mvn clean install -Dcheckstyle.skip
```

In the target directory this generates an OAR file named onos-app-dcnet-2.1.0.oar that can be used by ONOS. Change directory back to onos-2.1.0/bin, and use the command:
```
./onos-app 127.0.0.1 reinstall! <Path to oar>
```

Which installs the DCnet application into the ONOS controller and activates it. If the application needs to be uninstalled, use the command:
```
./onos-app 127.0.0.1 uninstall org.onosproject.dcnet
```

Once installed, it might be necessary to restart the ONOS controller so that it can read the configuration files set up by Mininet and add the switches from Mininet. Use ctrl-C on the terminal running onos-service and enter the command again to restart ONOS. After this, hosts should be able to ping each other on Mininet, and after the first ping packet is transmitted between hosts, the necessary translation rules will be installed by DCnet to make further pinging much quicker.

## Installing DCarp Application (Optional)
For some topologies, default implementations of ARP may not function correctly, in which case DCarp can be used to handle ARP requests instead. Build and install this application by changing the directory to dcnet-source/dcarp and following the same steps as with the DCnet Application.

To uninstall the application, use the command:
```
./onos-app 127.0.0.1 uninstall org.onosproject.dcarp
```
