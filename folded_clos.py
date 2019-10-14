from mininet.net import Mininet
from mininet.topo import Topo
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.node import RemoteController, Node, Host, OVSKernelSwitch
from mininet.link import TCLink

from mininet.link import TCLink
from argparse import ArgumentParser
import traceback
import random
import time
import json

# Function to parse the command line arguments
def parseOptions():
	leaf = []
	spine = []
	pod = []
	ss_ratio = []
	fanout = []
	dc = 2
	remote = []
	test = False

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
	parser.add_argument("--test", help = "Enable automatic testing")

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
		test = True

	# return the values
	return leaf, spine, pod, ss_ratio, fanout, dc, remote, test

def createTraffic(shuffle, host):
	h = 0
	while h < len(shuffle):
		server = shuffle[h]
		client = shuffle[h + 1]
		if server.name == host.name or client.name == host.name:
			h += 2
			continue
		server.cmd("iperf3 -s -1 &")
		client.cmd("iperf3 -t 25 -c " + server.IP() + " &")
		h += 2

def runPingTests(net, pods, dcs, with_load):
	host = net.hosts[0]
	if with_load is True:
		ping_out = open("ping_test_with_load.out", "w+")
	else:
		ping_out = open("ping_test_no_load.out", "w+")
	shuffle = list(net.hosts)
	random.shuffle(shuffle)
	print("Ping Test 1")
	if with_load is True:
		createTraffic(shuffle, host)
	ping_out.write("\n--- Ping Test 1 Results ---")
	ping_out.write(host.cmd("ping -c 3 " + net.hosts[1].IP()))
	print("Ping Test 2")
	ping_out.write("\n--- Ping Test 2 Results ---")
	ping_out.write(host.cmd("ping -c 20 " + net.hosts[1].IP()))
	time.sleep(10)
	print("Ping Test 3")
	if with_load is True:
		createTraffic(shuffle, host)
	ping_out.write("\n--- Ping Test 3 Results ---")
	ping_out.write(host.cmd("ping -c 3 " + net.hosts[len(net.hosts)/(dcs*pods)-1].IP()))
	print("Ping Test 4")
	ping_out.write("\n--- Ping Test 4 Results ---")
	ping_out.write(host.cmd("ping -c 20 " + net.hosts[len(net.hosts)/(dcs*pods)-1].IP()))
	time.sleep(10)
	print("Ping Test 5")
	if with_load is True:
		createTraffic(shuffle, host)
	ping_out.write("\n--- Ping Test 5 Results ---")
	ping_out.write(host.cmd("ping -c 3 " + net.hosts[len(net.hosts) / dcs - 1].IP()))
	print("Ping Test 6")
	ping_out.write("\n--- Ping Test 6 Results ---")
	ping_out.write(host.cmd("ping -c 20 " + net.hosts[len(net.hosts) / dcs - 1].IP()))
	time.sleep(10)
	print("Ping Test 7")
	if with_load is True:
		createTraffic(shuffle, host)
	ping_out.write("\n--- Ping Test 7 Results ---")
	ping_out.write(host.cmd("ping -c 3 " + net.hosts[-1].IP()))
	print("Ping Test 8")
	ping_out.write("\n--- Ping Test 8 Results ---")
	ping_out.write(host.cmd("ping -c 20 " + net.hosts[-1].IP()))
	time.sleep(10)

def runTCPTests(net, pods, dcs, with_load):
	client = net.hosts[0]
	if with_load is True:
		tcp_out = open("tcp_test_with_load.out", "w+")
	else:
		tcp_out = open("tcp_test_no_load.out", "w+")
	shuffle = list(net.hosts)
	random.shuffle(shuffle)
	print("TCP Test 1")
	if with_load is True:
		createTraffic(shuffle, client)
	server = net.hosts[1]
	server.cmd("iperf3 -s -1 -p 5250 &")
	tcp_out.write("\n--- TCP Test 1: ")
	tcp_out.write(client.name + " sending to " + server.name + " ---\n")
	tcp_out.write(client.cmd("iperf3 -t 20 -p 5250 -c " + server.IP()))
	time.sleep(10)
	print("TCP Test 2")
	if with_load is True:
		createTraffic(shuffle, client)
	server = net.hosts[len(net.hosts)/(dcs * pods) - 1]
	server.cmd("iperf3 -s -1 -p 5250 &")
	tcp_out.write("\n--- TCP Test 2: ")
	tcp_out.write(client.name + " sending to " + server.name + " ---\n")
	tcp_out.write(client.cmd("iperf3 -t 20 -p 5250 -c " + server.IP()))
	time.sleep(10)
	print("TCP Test 3")
	if with_load is True:
		createTraffic(shuffle, client)
	server = net.hosts[len(net.hosts)/dcs - 1]
	server.cmd("iperf3 -s -1 -p 5250 &")
	tcp_out.write("\n--- TCP Test 3: ")
	tcp_out.write(client.name + " sending to " + server.name + " ---\n")
	tcp_out.write(client.cmd("iperf3 -t 20 -p 5250 -c " + server.IP()))
	time.sleep(10)
	print("TCP Test 4")
	if with_load is True:
		createTraffic(shuffle, client)
	server = net.hosts[len(net.hosts) - 1]
	server.cmd("iperf3 -s -1 -p 5250 &")
	tcp_out.write("\n--- TCP Test 4: ")
	tcp_out.write(client.name + " sending to " + server.name + " ---\n")
	tcp_out.write(client.cmd("iperf3 -t 20 -p 5250 -c " + server.IP()))
	time.sleep(10)

def generateMac(switch_id):
	mac_addr = "00:00:" + format((switch_id >> 24) & 0xFF, "02x")
	mac_addr += ":" + format((switch_id >> 16) & 0xFF, "02x")
	mac_addr += ":" + format((switch_id >> 8) & 0xFF, "02x")
	mac_addr += ":" + format(switch_id & 0xFF, "02x")
	return mac_addr


# Class defining a Folded Clos topology using super spines
class FoldedClos(Topo):
	def __init__(self, leaf, spine, pod, ss_ratio, fanout, dc, remote):
		"Create Leaf and Spine Topo."

		Topo.__init__(self)

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
		top_config = {"dc_count" : dc,"config" : []}
		for i in range(dc):
			top_config["config"].append({
				"dc_radix_down" : spine[i] * ss_ratio[i],
				"ss_radix_down" : pod[i],
				"sp_radix_up" : ss_ratio[i],
				"sp_radix_down" : leaf[i],
				"lf_radix_up" : spine[i],
				"lf_radix_down" : fanout[i]
			})
		config = open("config/mininet/top_config.json", "w+")
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
			switch_count += 1;
			dc_switches.append(dc_name)
			switch_config["dcs"].append({
				"id" : format(dc_count, "x"),
				"name" : dc_name,
				"mac" : generateMac(dc_count),
				"dc" : d,
				"pod" : -1,
				"leaf" : -1
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
				switch_count += 1;
				ss_switches[d].append(ss_name)
				switch_config["supers"].append({
					"id" : format(ss_count, "x"),
					"name" : ss_name,
					"mac" : generateMac(ss_count),
					"dc" : d,
					"pod" : -1,
					"leaf" : -1
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
						"leaf" : l
					})
					leaf_count += increment
					
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
						self.addLink(leaf_name, host_name,
									cls = TCLink, bw = 10, delay = "1ms")
	
				# Create spines and link to super spines and leaves
				for s in range(spine[d]):
					spine_name = "s" + str(spine_count)
					ip_addr = "10.0.1" + format(switch_count >> 8, "02d") + "."
					ip_addr += format(switch_count & 0xFF, "d") + "/12"
					self.addSwitch(spine_name, ip = ip_addr)
					switch_count += 1;
					switch_config["spines"].append({
						"id" : format(spine_count, "x"),
						"name" : spine_name,
						"mac" : generateMac(spine_count),
						"dc" : d,
						"pod" : p,
						"leaf" : -1
					})
					spine_count += increment
					for l in range(leaf[d]):
						self.addLink(spine_name, leaf_switches[d][l + p*leaf[d]],
										cls = TCLink, bw = 40, delay = "1ms")
					for ss in range(ss_ratio[d]):
						self.addLink(ss_switches[d][ss + s*ss_ratio[d]],
										spine_name, cls = TCLink, bw = 40, delay = "1ms")
			
			# Link super spines to data center router
			for ss in range(ss_ratio[d] * spine[d]):
				ss_name = ss_switches[d][ss]
				self.addLink(dc_name, ss_name, cls = TCLink, bw = 100, delay = "1ms")
		
		# Let a single high bandwidth, high latency link represent
		# an internet connection between each pair of data centers	
		for d1 in range(dc):
			for d2 in range(d1 + 1, dc):
				if (remote[d1] == 0) & (remote[d2]) == 0:
					self.addLink(dc_switches[d1], dc_switches[d2],
								cls = TCLink, bw = 1000, delay = "1ms")
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
						cls = TCLink, bw = 10, delay = "1ms")

		config = open("config/mininet/switch_config.json", "w+")
		config.write(json.dumps(switch_config, indent = 4))
		config = open("config/mininet/host_config.json", "w+")
		config.write(json.dumps(host_config, indent = 4))

if __name__ == "__main__":
	net = None
	try:
		setLogLevel("info")
		leaf, spine, pod, ss_ratio, fanout, dc, remote, test = parseOptions()
		topo = FoldedClos(leaf, spine, pod, ss_ratio, fanout, dc, remote)
		net = Mininet(topo, controller = RemoteController, link = TCLink)
		net.start()

		# Assign IPv6 addresses based on DCnet specifications
		for h in range(len(net.hosts)):
			host = net.hosts[h]
			command = "ifconfig " + host.name + "-eth0 inet6 add dcdc::dc"
			command += format(((h + 1) >> 16) & 0xFF, "02x")
			command += ":" + format((h + 1) & 0xFFFF, "04x") + "/104"
			host.cmd(command)

		# Run ping and TCP tests
		if test is True:
                        time.sleep(30)
			print("*** Running performance tests (no load)")
			runPingTests(net, pod[0], dc, False)
			runTCPTests(net, pod[0], dc, False)
		
			print("*** Running performance tests (with load)")
			runPingTests(net, pod[0], dc, True)
			runTCPTests(net, pod[0], dc, True)

		CLI(net)
	finally:
		if net is not None:
			net.stop()


topos = {'FoldedClos':
		(lambda leaf, spine, pod, ss_ratio, fanout, dc:
		FoldedClos(leaf, spine, pod, ss_ratio, fanout, dc))}

