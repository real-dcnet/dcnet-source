from mininet.net import Mininet
from mininet.topo import Topo
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.node import RemoteController, Node, Host, OVSKernelSwitch

from mininet.link import TCLink
from argparse import ArgumentParser
import traceback
import random
import time
import datetime
import multiprocessing as mp
import json
import os

# Function to parse the command line arguments
def parseOptions():
	leaf = []
	spine = []
	pod = []
	ss_ratio = []
	fanout = []
	dc = 2
	remote = []
	test = ""
        run_ping = False
        with_traffic = False
        high_latency = 50

	parser = ArgumentParser("Create a folded Clos network topology")

	# Add arguments to the parser for leaf, spine, pod, super spine, and fanout options
	parser.add_argument("--leaf", type = int, nargs = "+",
						help = "Number of leaf switches per pod")
	parser.add_argument("--spine", type = int, nargs = "+",
						help = "Number of spine switches per pod")
	parser.add_argument("--pod", type = int, nargs = "+",
						help = "Number of pods per data center")
	parser.add_argument("--ratio", type = int, nargs = "+",
						help = "Number of super spine switches per spine switch")
	parser.add_argument("--fanout", type = int, nargs = "+", 
						help = "Number of hosts per leaf switch")
	parser.add_argument("--dc", type = int, help = "Number of data centers")
	parser.add_argument("--remote", type = int, nargs = "+",
						help = "Value 1 or 0. Indicates if a data center is remote")
	parser.add_argument("--test", type = str, help = "Enable automatic testing")
        parser.add_argument("--run_ping", type = str, help = "True or False. Indicates if you want to run ping test or tcp test")

        parser.add_argument("--with_traffic", type = str, help = "True or False. Indicates if you want other traffic on the network for TCP tests")
        parser.add_argument("--high_latency", type = str, help = "Integer value. Indicates the latency between datacenters")

	args = parser.parse_args()

	# Change the values if passed on command line
	if args.dc:
		dc = args.dc
	for i in range(dc):
		leaf.append(4)
		spine.append(2)
		pod.append(2)
		ss_ratio.append(2)
		fanout.append(3)
		remote.append(0)
	if args.leaf:
		leaf = []
		if len(args.leaf) == dc: 
			leaf = args.leaf
		else:
			for i in range(dc):
				leaf.append(args.leaf[0])
	if args.spine:
		spine = []
		if len(args.spine) == dc: 
			spine = args.spine
		else:
			for i in range(dc):
				spine.append(args.spine[0])
	if args.pod:
		pod = []
		if len(args.pod) == dc: 
			pod = args.pod
		else:
			for i in range(dc):
				pod.append(args.pod[0])
	if args.ratio:
		ss_ratio = []
		if len(args.ratio) == dc: 
			ss_ratio = args.ratio
		else:
			for i in range(dc):
				ss_ratio.append(args.ratio[0])
	if args.fanout:
		fanout = []
		if len(args.fanout) == dc: 
			fanout = args.fanout
		else:
			for i in range(dc):
				fanout.append(args.fanout[0])
	if args.remote:
		remote = []
		if len(args.remote) == dc: 
			remote = args.remote
		else:
			for i in range(dc):
				remote.append(args.remote[0])
	if args.test:
		test = args.test

        if args.run_ping and args.run_ping == "true":
                run_ping = True

        if args.with_traffic and args.with_traffic == "true":
                with_traffic = True

        if args.high_latency:
                high_latency = args.high_latency

	# return the values
	return leaf, spine, pod, ss_ratio, fanout, dc, remote, test, run_ping, with_traffic, high_latency


def createTraffic(shuffle):#, src, dst):
	h = 0
        #int src_switch = src / 3
        #int dst_switch = dst / 3
        #int src_i = src - src % 3
        #let sources = range(src_switch * 3 
	while h < len(shuffle) - 1:
		server = shuffle[h]
		client = shuffle[h + 1]
		server.cmd("iperf3 -s -1 &")
                client.cmd("iperf3 -w 200k -t 35 -c " + server.IP() + " &")
                h += 2

def startServer(server):
    server.cmd("nohup ~/iperf-3.10.1/src/iperf3 -s -1 >> ./server.txt &")
    output = server.cmd("echo $!")
    output = output.strip() # Remove the trailing newline
    #output = output.split()
    print("output1: ", output)
    # Return PID
    return output

def startClient(client, server):
    # 8960 is MSS of jumbo ethernet 
    client.cmd("nohup ~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 200000K -t 11 -c " + server.IP() + " >>  ./client.txt &")
    output = client.cmd("echo $!")
    output = output.strip() # Remove the trailing newline
    print("output2: ", output)
    # Return PID
    return output

def createTcpTestTraffic(shuffle):
        h = 0
        procs = []
        
        # TODO: Comment out for not doing baseline tests
        #print("NAME: ", shuffle[0].name) # prints h29
        dc_1_hosts = []
        dc_2_hosts = []
        # Only works for two datacenters
        for host in shuffle:
            if int(host.name[1:]) <= 17:
                dc_1_hosts.append(host)
            else: # if int(host.name[1:]) > 35:
                dc_2_hosts.append(host)

        #print("shuffle is: ", shuffle)
        #print("dc_1_hosts: ", dc_1_hosts)
        #print("len dc_1: ", len(dc_1_hosts))
        #print("dc_2_hosts: ", dc_2_hosts)
        #print("len dc_2: ", len(dc_2_hosts))
        #while h < len(shuffle) - 1:
        while h < len(dc_1_hosts):
                #server = shuffle[h]
                #client = shuffle[h + 1]
                # Uncomment for normal, random traffic
                #procs.append(startServer(server))
                #procs.append(startClient(client, server))

                # Controlled traffic in direction of destination
                print(dc_1_hosts[h], ' sending to ', dc_2_hosts[h])
                procs.append(startServer(dc_2_hosts[h]))
                procs.append(startClient(dc_1_hosts[h], dc_2_hosts[h]))

                # TODO: Change this to modify the number of hosts on the system
                h += len(dc_1_hosts)
                #h += 2
        return procs
    
def runPingTests(net, leaf, pod, fanout, dc, with_load, out_dir):
	host = net.hosts[0]
	destinations = [1, fanout[0], (leaf[0] * fanout[0])]
	host_count = pod[0] * leaf[0] * fanout[0]
	for i in range(dc - 1):
		destinations.append(host_count)
		host_count += pod[i + 1] * leaf[i + 1] * fanout[i + 1]
	if with_load is True:
		ping_out = open(out_dir + "/ping_test_with_load.out", "w+")
	else:
		ping_out = open(out_dir + "/ping_test_no_load.out", "w+")
        ################################################ RESTORE WHAT WAS HERE ############
        
        # TODO: REMOVE
        #raw_input("First raw input")
        #print("about to sleep")
        #time.sleep(10)
        
        # Yes this will install a rule for itself... oh well
        # Is this necessary with auto static arp?
        '''for i in range(0, len(net.hosts)):
            # Install ARP entries into each host
            source = net.hosts[i]
            for j in range(0, len(net.hosts)):
                destIP = net.hosts[j].IP()
                # print("destIP: ", destIP)
                destMAC = net.hosts[j].MAC
                source.setARP(destIP, "00:00:00:00:00:00")
           '''    

        for i in range(0, len(destinations)):
		# Shuffle list so we mix up src/dest combos each time
                shuffle = list(net.hosts)
		random.shuffle(shuffle)
	        
                # TODO: REMOVE
                #input("Press Enter to continue again...")
                #host.setARP("10.0.0.2", "00:00:00:00:00:00")
                #net.hosts[destinations[i]].setARP("10.0.0.1", "00:00:00:00:00:00")
                #host.setARP("10.0.0.4", "00:00:00:00:00:00")
                #raw_input("Press Enter to continue...")
                print("Ping Test " + str(2 * i + 1))
		if with_load is True:
			createTraffic(shuffle, host)
                time.sleep(2)
		ping_out.write("\n--- Ping Test " + str(2 * i + 1) + " Results ---\n")
                # TODO: Replace with what it was!
                print(host.cmd("ping -c 5 " + net.hosts[destinations[i]].IP()))
                ping_out.write(host.cmd("ping -c 5 " + net.hosts[destinations[i]].IP()))
		time.sleep(1)
                #print(host.cmd("ping -c 50 " + net.hosts[destinations[i]].IP()))
		print("Ping Test " + str(2 * i + 2))
		ping_out.write("\n--- Ping Test " + str(2 * i + 2) + " Results ---\n")
		print(host.cmd("ping -c 50 " + net.hosts[destinations[i]].IP()))
		time.sleep(4)

def runTCPTests(net, leaf, pod, fanout, dc, with_load, out_dir):
	client = net.hosts[0]
        print("HOSTS ARE: ", net.hosts)
        destinations = [1, fanout[0], (leaf[0] * fanout[0])]
        destination_hosts = []
        for i in range(len(destinations)):
            destination_hosts.append(net.hosts[destinations[i]])
	print("Finished starting the traffic")
        host_count = pod[0] * leaf[0] * fanout[0]
	for i in range(dc - 1):
                destination_hosts.append(net.hosts[host_count]) # Hosts that the sender sends to
		destinations.append(host_count)                 # Indices of the destination hosts
		host_count += pod[i + 1] * leaf[i + 1] * fanout[i + 1]
        #if with_load is True:
	#	tcp_out = open(out_dir + "/tcp_test_with_load.out", "w+")
	#else:
        
        # FOR UNIDIRECTIONAL TEST ONLY
        tcp_out = open(out_dir + "/tcp_test_no_load.out", "w+")
        receiver_out = open(out_dir + "/tcp_receive_no_load.out", "w+")

        # FOR BIDIRECTIONAL TEST ONLY
        host1_send_out = open(out_dir + "/host1_send.out", "a+")
        host1_receive_out = open(out_dir + "/host1_receive.out", "a+")
        host2_send_out = open(out_dir + "/host2_send.out", "a+")
        host2_receive_out = open(out_dir + "/host2_receive.out", "a+")

	others = []
        print("upper limit, ", len(net.hosts) - dc)
        for i in range(1, len(net.hosts) - dc):
		#if i not in destinations: # SHOULD THIS BE net.hosts[i] not in
                    #   Well what this is doing is excluding the leaves we are attached to and that's not really what we want
                if i not in destination_hosts:
                    others.append(net.hosts[i])
        # Todo: Remove
        print("About to sleep")
        time.sleep(5)
        # TODO: CHANGE RANGE BACK TO 0, len(destinations)
        for i in range(len(destinations) - 1, len(destinations)):
		shuffle = list(others)
		random.shuffle(shuffle)
		print("\nTCP Test " + str(i))
                start_time = datetime.datetime.now()
                traffic_procs = []
                if with_load is True:
			traffic_procs = createTcpTestTraffic(shuffle)
		print("here now")
		server = net.hosts[destinations[i]]
                # FOR UNIDIRECTIONAL TEST ONLY
               
                # TODO: REMOVE

                # Send h1 to h2 and h3 to h4
                
                client2 = net.hosts[1] # h2
                server2 = net.hosts[22] # h23
                '''
                # TODO SWAP BACK
                server3 = net.hosts[2] # h3
                client3 = net.hosts[23] # h24

                # TODO SWAP BACK
                server4 = net.hosts[3] # h4
                client4 = net.hosts[24] # h25
                
                client5 = net.hosts[4] # h5
                server5 = net.hosts[25] # h26

                # TODO: NOTICE THESE SWAPPED AS WELL
                server6 = net.hosts[5] # h6
                client6 = net.hosts[26] # h27
                
                client7 = net.hosts[6] # h7
                server7 = net.hosts[27] # h28

                #TODO: Notice these are swapped
                server8 = net.hosts[7] # h8
                client8 = net.hosts[28] # h29
                
                client9 = net.hosts[8] # h9
                server9 = net.hosts[29] # h30

                #TODO: Notice these are swapped
                server10 = net.hosts[9] # h10
                client10 = net.hosts[30] # h31
                
                receiver10_write_location = out_dir + "/tcp_receive_no_load10.out &"
                server10.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver10_write_location)
                client10.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server10.IP() + " --logfile " + out_dir + "/tcp_send_no_load10.out &")

                receiver9_write_location = out_dir + "/tcp_receive_no_load9.out &"
                server9.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver9_write_location)
                client9.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server9.IP() + " --logfile " + out_dir + "/tcp_send_no_load9.out &")
                
                receiver8_write_location = out_dir + "/tcp_receive_no_load8.out &"
                server8.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver8_write_location)
                client8.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server8.IP() + " --logfile " + out_dir + "/tcp_send_no_load8.out &")

                receiver7_write_location = out_dir + "/tcp_receive_no_load7.out &"
                server7.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver7_write_location)
                client7.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server7.IP() + " --logfile " + out_dir + "/tcp_send_no_load7.out &")
                
                receiver6_write_location = out_dir + "/tcp_receive_no_load6.out &"
                server6.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver6_write_location)
                client6.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server6.IP() + " --logfile " + out_dir + "/tcp_send_no_load6.out &")

                receiver5_write_location = out_dir + "/tcp_receive_no_load5.out &"
                server5.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver5_write_location)
                client5.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server5.IP() + " --logfile " + out_dir + "/tcp_send_no_load5.out &")
                
                receiver4_write_location = out_dir + "/tcp_receive_no_load4.out &"
                server4.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver4_write_location)
                #print(client4.name + " second sending to " + server4.name)
                client4.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server4.IP() + " --logfile " + out_dir + "/tcp_send_no_load4.out &")
                
                
                receiver3_write_location = out_dir + "/tcp_receive_no_load3.out &"
                server3.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver3_write_location)
                #print(client3.name + " second sending to " + server3.name)
                client3.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server3.IP() + " --logfile " + out_dir + "/tcp_send_no_load3.out &")
                '''
                receiver2_write_location = out_dir + "/tcp_receive_no_load2.out &"
                server2.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver2_write_location)
                #print(client2.name + " second sending to " + server2.name)
                client2.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 10000K -t 10 -p 5250 -c " + server2.IP() + " --logfile " + out_dir + "/tcp_send_no_load2.out &")
                
                #print(client.cmd("ping -c 5 " + server.IP()))
                receiver_write_location = out_dir + "/tcp_receive_no_load.out &"
                server.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + receiver_write_location)
                #tcp_out.write("\n--- TCP Test " + str(i) + ": ")
                #print("Main: " + client.name + " sending to " + server.name)
		tcp_out.write(client.name + " sending to " + server.name + " ---\n")
                # max tcp window size was 200000K 
                # TODO: CHANGE BACK TO TCP_OUT write
                tcp_out.write(client.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w 15000K -t 10 -p 5250 -c " + server.IP()))
                
                
                # FOR BIDIRECTIONAL TEST ONLY
                window_size = "500K"
                ''' 
                host1_receive_write_loc = out_dir + "/host1_receive.out &"
                host2_receive_write_loc = out_dir + "/host2_receive.out &"
                server.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5250 -V --logfile " + host2_receive_write_loc)
                client.cmd("~/iperf-3.10.1/src/iperf3 -s -1 -p 5251 -V --logfile " + host1_receive_write_loc)

                host1_send_out.write("\n--- TCP Test " + str(i) + ": ")
                host1_send_out.write(client.name + " sending to " + server.name + " ---\n")

                client.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w " + window_size + " -t 10 -p 5250 -c " + server.IP() + " --logfile " + out_dir + "/host1_send.out &")

                host2_send_out.write("\n--- TCP Test " + str(i) + ": ")
                host2_send_out.write(server.name + " sending to " + client.name + " ---\n")

                server.cmd("~/iperf-3.10.1/src/iperf3 -M 9216 -V -w " + window_size + " -t 10 -p 5251 -c " + client.IP() + " --logfile " + out_dir + "/host2_send.out")
                '''
                print("Finished the main throughput send")
                end_time = datetime.datetime.now()
                print("End time: ", end_time-start_time)
                for p in traffic_procs:
                    # Wait for background processes to terminate
                    proc_name = "/proc/" + p
                    print("waiting for " + proc_name)
                    while (os.path.exists(proc_name)):
                       continue 
               # time.sleep(5)

def generateMac(switch_id):
	mac_addr = "00:00:" + format((switch_id >> 24) & 0xFF, "02x")
	mac_addr += ":" + format((switch_id >> 16) & 0xFF, "02x")
	mac_addr += ":" + format((switch_id >> 8) & 0xFF, "02x")
	mac_addr += ":" + format(switch_id & 0xFF, "02x")
	return mac_addr


# Class defining a Folded Clos topology using super spines
class FoldedClos(Topo):
	def __init__(self, leaf, spine, pod, ss_ratio, fanout, dc, remote, with_traffic, high_latency):

		"Create Leaf and Spine Topo."

		Topo.__init__(self)

                if (with_traffic):
                    switch_bw = 1000
                else:
                    switch_bw = 1000#40

                if (with_traffic):
                    host_bw = 1000
                else:
                    host_bw = 1000#10

                super_spline_bw = 1000

                link_delay = "1ms"
                queue_size = 1000

		# Simple counter for assigning host names
		host_count = 1
		switch_count = 1;

		# Counter with adjustable increments for switch names. Choose
		# increment and initial values to easily identify switch types
		increment = 16
		leaf_count = 10 + increment
		spine_count = 11 + increment
		ss_count = 12 + increment
		dc_count = 13 + increment

		# Configuration file for topology that can be used by SDN controller
		#top_config = open("config/mininet/top_config.csv", "w+")
		top_config = {"dc_count" : dc, "offset" : (dc * pod[0] * leaf[0])/2.0, "config" : []}
		for i in range(dc):
			top_config["config"].append({
				"dc_radix_down" : spine[i] * ss_ratio[i],
				"ss_radix_down" : pod[i],
				"sp_radix_up" : ss_ratio[i],
				"sp_radix_down" : leaf[i],
				"lf_radix_up" : spine[i],
				"lf_radix_down" : fanout[i]
			})
		config = open("../../config/mininet/top_config.json", "w+")
		config.write(json.dumps(top_config, indent = 4))
		# Configuration file for switches that can be used by SDN controller
		#switch_config = open("config/mininet/switch_config.csv", "w+")
		switch_config = {"dcs" : [], "supers" : [], "spines" : [], "leaves" : []}
		
		# Configuration file for hosts that can be used by SDN controller
		#host_config = open("config/mininet/host_config.csv", "w+")
		host_config = {"hosts" : []}
		
		dc_switches = []
		ss_switches = []
		leaf_switches = []

		for d in range(dc):
			dc_name = "d" + str(dc_count)
			ip_addr = "10.0.1" + format(switch_count >> 8, "02d") + "."
			ip_addr += format(switch_count & 0xFF, "d") + "/12"
			self.addSwitch(dc_name, ip = ip_addr)
			switch_count += 1
			dc_switches.append(dc_name)
			switch_config["dcs"].append({
				"id" : format(dc_count, "x"),
				"name" : dc_name,
				"mac" : generateMac(dc_count),
				"dc" : d,
				"pod" : -1,
				"leaf" : -1,
				"longitude" : (d * pod[d] * leaf[d]) + (1.0 * pod[d] * leaf[d])/2
			})
			dc_count += increment
			ss_switches.append([])
			leaf_switches.append([])

			# Create super spines
			for ss in range(ss_ratio[d] * spine[d]):
				ss_name = "u" + str(ss_count)
				ip_addr = "10.0.1" + format(switch_count >> 8, "02d") + "."
				ip_addr += format(switch_count & 0xFF, "d") + "/12"
				self.addSwitch(ss_name, ip = ip_addr)
				switch_count += 1
				ss_switches[d].append(ss_name)
				switch_config["supers"].append({
					"id" : format(ss_count, "x"),
					"name" : ss_name,
					"mac" : generateMac(ss_count),
					"dc" : d,
					"pod" : -1,
					"leaf" : -1,
					"longitude" : (d * pod[d] * leaf[d])
								+ (1.0 * ss * (pod[d] * leaf[d] - 1))/(ss_ratio[d] * spine[d] - 1)
				})
				ss_count += increment

			# Create a group of leaf and spine switches for every pod
			for p in range(pod[d]):

				# Create leaves and hosts for each leaf
				for l in range(leaf[d]):
					leaf_name = "l" + str(leaf_count)
					ip_addr = "10.0.1" + format(switch_count >> 8, "02d") + "."
					ip_addr += format(switch_count & 0xFF, "d") + "/12"
					self.addSwitch(leaf_name, ip = ip_addr)
					switch_count += 1;
					leaf_switches[d].append(leaf_name)
					switch_config["leaves"].append({
						"id" : format(leaf_count, "x"),
						"name" : leaf_name,
						"mac" : generateMac(leaf_count),
						"dc" : d,
						"pod" : p,
						"leaf" : l,
						"longitude" : (d * pod[d] * leaf[d] + p * leaf[d]) + l
					})
					leaf_count += increment
	
				# Create spines and link to super spines and leaves
				for s in range(spine[d]):
					spine_name = "s" + str(spine_count)
					ip_addr = "10.0.1" + format(switch_count >> 8, "02d") + "."
					ip_addr += format(switch_count & 0xFF, "d") + "/12"
					self.addSwitch(spine_name, ip = ip_addr)
					switch_count += 1
					switch_config["spines"].append({
						"id" : format(spine_count, "x"),
						"name" : spine_name,
						"mac" : generateMac(spine_count),
						"dc" : d,
						"pod" : p,
						"leaf" : -1,
						"longitude" : (d * pod[d] * leaf[d] + p * leaf[d]) + (1.0 * s * (leaf[d] - 1))/(spine[d] - 1)
					})
					spine_count += increment
					for l in range(leaf[d]):
						self.addLink(spine_name, leaf_switches[d][l + p*leaf[d]],
										cls = TCLink, bw = switch_bw, max_queue_size=queue_size, delay = link_delay)
					for ss in range(ss_ratio[d]):
						self.addLink(ss_switches[d][ss + s*ss_ratio[d]],
										spine_name, cls = TCLink, bw = switch_bw, max_queue_size=queue_size, delay = link_delay)

				for l in range(leaf[d]):
					# Create hosts, designated by letter h, and link to leaf
					for h in range(fanout[d]):
						host_name = "h" + str(host_count)

						# Construct host IPv4 address, first 8 bits are reserved,
						# last 24 bits uniquely identify a host
						ip_addr = "10.0." + format(host_count >> 8, "d") + "."
						ip_addr += format(host_count & 0xFF, "d")
	
						# Construct host UID MAC address, first 24 bits are reserved,
						# last 24 bits uniquely identify a host
						mac_addr = "dc:dc:dc:" + format((host_count >> 16) & 0xFF, "02x")
						mac_addr += ":" + format((host_count >> 8) & 0xFF, "02x")
						mac_addr += ":" + format(host_count & 0xFF, "02x")
	
						# Construct host RMAC address based on dc, pod, leaf, and host
						# First 2 bits are type (unused), next 10 are the data center id,
						# next 12 are pod the number, next 12 are the leaf number, and
						# last 12 are the host number
						rmac_addr = format((d >> 4) & 0x3F, "02x") + ":"
						rmac_addr += format(d & 0xF, "01x")
						rmac_addr += format((p >> 8) & 0xF, "01x") + ":"
						rmac_addr += format(p & 0xFF, "02x") + ":"
						rmac_addr += format((l >> 4) & 0xFF, "02x") + ":"
						rmac_addr += format(l & 0xF, "01x")
						rmac_addr += format((h >> 8)& 0xF, "01x") + ":"
						rmac_addr += format(h & 0xFF, "02x")
	
						self.addHost(host_name, ip = ip_addr + "/12", mac = mac_addr)
						host_config["hosts"].append({
							"ip" : ip_addr,
							"name" : host_name,
							"rmac" : rmac_addr,
							"idmac" : mac_addr
						})
						host_count += 1
						self.addLink(leaf_switches[d][l + p*leaf[d]], host_name,
                                                        cls = TCLink, bw = host_bw, max_queue_size=queue_size, delay = link_delay)
			
			# Link super spines to data center router
			for ss in range(ss_ratio[d] * spine[d]):
				ss_name = ss_switches[d][ss]
                                # Todo: change back: was 100 for bw
				self.addLink(dc_name, ss_name, cls = TCLink, bw = super_spline_bw, delay = link_delay)
		
		# Let a single high bandwidth, high latency link represent
		# an internet connection between each pair of data centers
                high_latency_link = high_latency + "ms"
                print("HIGH LATENCY LINK IS: ", high_latency_link)
                

		for d1 in range(dc):
			for d2 in range(d1 + 1, dc):
				if (remote[d1] == 0) & (remote[d2]) == 0:
					self.addLink(dc_switches[d1], dc_switches[d2],
                                                cls = TCLink, bw = super_spline_bw, delay = high_latency_link)# TODO: CHECK WHY THIS WAS THIS WAY!link_delay)
				else:
					self.addLink(dc_switches[d1], dc_switches[d2],
								cls = TCLink, bw = super_spline_bw, delay = high_latency_link)
			host_name = "h" + str(host_count)
			ip_addr = "10.0." + format(host_count >> 8, "d") + "."
			ip_addr += format(host_count & 0xFF, "d")
			mac_addr = "dc:dc:dc:" + format((host_count >> 16) & 0xFF, "02x")
			mac_addr += ":" + format((host_count >> 8) & 0xFF, "02x")
			mac_addr += ":" + format(host_count & 0xFF, "02x")
			self.addHost(host_name, ip = ip_addr, mac = mac_addr)
			host_count += 1
			self.addLink(dc_switches[d1], host_name,
						cls = TCLink, bw = host_bw, delay = link_delay)

		config = open("../../config/mininet/switch_config.json", "w+")
		config.write(json.dumps(switch_config, indent = 4))
		config = open("../../config/mininet/host_config.json", "w+")
		config.write(json.dumps(host_config, indent = 4))

if __name__ == "__main__":
	net = None
	try:
		setLogLevel("info")
		leaf, spine, pod, ss_ratio, fanout, dc, remote, test, run_ping, with_traffic, latency = parseOptions()
                topo = FoldedClos(leaf, spine, pod, ss_ratio, fanout, dc, remote, with_traffic, latency)
		net = Mininet(topo, switch = OVSKernelSwitch, controller = RemoteController, link = TCLink, autoStaticArp = True)
		net.start()

		# Assign IPv6 addresses based on DCnet specifications
		for h in range(len(net.hosts)):
			host = net.hosts[h]
			command = "ifconfig " + host.name + "-eth0 inet6 add dcdc::dc"
			command += format(((h + 1) >> 16) & 0xFF, "02x")
			command += ":" + format((h + 1) & 0xFFFF, "04x") + "/104"
			host.cmd(command)

                # Run ping and TCP tests
		if test:
			tests_path = os.path.join(os.getcwd(), "test")
			if not os.path.exists(tests_path):
				os.makedirs(tests_path)
			time.sleep(10)
                        if (with_traffic):
			    print("*** Running performance tests (with load)")
                        else:
                            print("*** Running performance tests (no load)")
                        
                        if (run_ping):
			    runPingTests(net, leaf, pod, fanout, dc, with_traffic, "test")
                        else:
                            runTCPTests(net, leaf, pod, fanout, dc, with_traffic, "test")

                elif not test:
	                CLI(net)

	finally:
		if net is not None:
			net.stop()


topos = {'FoldedClos':
		(lambda leaf, spine, pod, ss_ratio, fanout, dc:
		FoldedClos(leaf, spine, pod, ss_ratio, fanout, dc))}

