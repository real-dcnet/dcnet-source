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

	# return the values
	return leaf, spine, pod, ss_ratio, fanout, dc, remote, test, run_ping, with_traffic

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
    server.cmd("iperf3 -s -1  &")
    output = server.cmd("echo $!")
    output = output.strip() # Remove the trailing newline
    #output = output.split()
    print("output1: ", output)
    return output
    if (len(output) < 2):
        return "oof 1"
    # Return the pid
    return output[1]

def startClient(client, server):
    # 8960 is MSS of jumbo ethernet 
    client.cmd("iperf3 -M 8960 -w 500KB -t 20 -c " + server.IP() + " &")
    output = client.cmd("echo $!")
    output = output.strip() # Remove the trailing newline
    #output = output.split()
    print("output2: ", output)
    return output
    # Return the pid
    if (len(output) < 2):
        return "oof"
    return output[1]

def createTcpTestTraffic(shuffle):
        h = 0
        procs = []
        while h < len(shuffle) - 1:
                server = shuffle[h]
                client = shuffle[h + 1]
                #server_proc = mp.Process(target=startServer, args=(server,))
                #server_proc.start()
                #procs.append(server_proc)
                #client_proc = mp.Process(target=startClient, args=(client,server,))
                #client_proc.start()
                #procs.append(client_proc)
                procs.append(startServer(server))
                procs.append(startClient(client, server))
                h += 2
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
        tcp_out = open(out_dir + "/tcp_test_no_load.out", "w+")
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
        for i in range(len(destinations)):
		shuffle = list(others)
		random.shuffle(shuffle)
		print("TCP Test " + str(i))
                start_time = datetime.datetime.now()
                traffic_procs = []
                if with_load is True:
			traffic_procs = createTcpTestTraffic(shuffle)
		print("here now")
                time.sleep(5)
		server = net.hosts[destinations[i]]
		#print(client.cmd("ping -c 5 " + server.IP()))
                server.cmd("iperf3 -s -1 -p 5250 &")
		tcp_out.write("\n--- TCP Test " + str(i) + ": ")
		tcp_out.write(client.name + " sending to " + server.name + " ---\n")
                tcp_out.write(client.cmd("iperf3 -M 9216 -V -w 1024K -t 10 -l 1000K -p 5250 -c " + server.IP()))
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
	def __init__(self, leaf, spine, pod, ss_ratio, fanout, dc, remote, with_traffic):

		"Create Leaf and Spine Topo."

		Topo.__init__(self)

                if (with_traffic):
                    switch_bw = 1000
                else:
                    switch_bw = 40

                if (with_traffic):
                    host_bw = 1000
                else:
                    host_bw = 10

                link_delay = "1ms"
                queue_length = 1

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
										cls = TCLink, bw = switch_bw, max_queue_size=1000, delay = link_delay)
					for ss in range(ss_ratio[d]):
						self.addLink(ss_switches[d][ss + s*ss_ratio[d]],
										spine_name, cls = TCLink, bw = switch_bw, max_queue_size=1000, delay = link_delay)

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
                                                        cls = TCLink, bw = host_bw, max_queue_size=1000, delay = link_delay)
			
			# Link super spines to data center router
			for ss in range(ss_ratio[d] * spine[d]):
				ss_name = ss_switches[d][ss]
                                # Todo: change back: was 100 for bw
				self.addLink(dc_name, ss_name, cls = TCLink, bw = 1000, delay = link_delay)
		
		# Let a single high bandwidth, high latency link represent
		# an internet connection between each pair of data centers	
		for d1 in range(dc):
			for d2 in range(d1 + 1, dc):
				if (remote[d1] == 0) & (remote[d2]) == 0:
					self.addLink(dc_switches[d1], dc_switches[d2],
								cls = TCLink, bw = 1000, delay = link_delay)
				else:
					self.addLink(dc_switches[d1], dc_switches[d2],
								cls = TCLink, bw = 1000, delay = "50ms")
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
		leaf, spine, pod, ss_ratio, fanout, dc, remote, test, run_ping, with_traffic = parseOptions()
                topo = FoldedClos(leaf, spine, pod, ss_ratio, fanout, dc, remote, with_traffic)
		net = Mininet(topo, switch = OVSKernelSwitch, controller = RemoteController, link = TCLink)#, autoStaticArp = True)
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

