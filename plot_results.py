from argparse import ArgumentParser
import json

data = {}
parser = ArgumentParser("Parse data from ping and tcp output files")
parser.add_argument("--ping", type = str, help = "Location of ping output")
parser.add_argument("--tcp", type = str, help = "Location of tcp output")

args = parser.parse_args()

# Replace 58 with the actual number of hops to remote data center
hops = [2, 4, 6, 9, 58]

if args.ping:
	count = 0
	data["initial_ping"] = []
	data["steady_ping"] = []
	ping = open(args.ping, "r")
	for line in ping.readlines():
		fields = line.split(" ")
		if fields[0] == "rtt":
			stats = fields[3].split("/")
			entry = {"min": float(stats[0]), "avg": float(stats[1]),
						"max": float(stats[2]), "dev": float(stats[3])}
			if count/2 < len(hops):
				entry["hops"] = hops[count/2]
			else:
				entry["hops"] = hops[len(hops)]
			if count % 2 == 0:
				data["initial_ping"].append(entry)
			else:
				data["steady_ping"].append(entry)
			count += 1
	ping.close()

if args.tcp:
	data["tcp_send"] = []
	data["tcp_receive"] = []
	tcp = open(args.ping, "r")
	for line in tcp.readlines():
		fields = line.split(" ")
		if fields[len(fields)] == "sender":
			stats = fields[3].split("/")
			entry = {"min": float(stats[0]), "avg": float(stats[1]),
						"max": float(stats[2]), "dev": float(stats[3])}
			if count/2 < len(hops):
				entry["hops"] = hops[count/2]
			else:
				entry["hops"] = hops[len(hops)]
			if count % 2 == 0:
				data["initial_ping"].append(entry)
			else:
				data["steady_ping"].append(entry)
			count += 1
print json.dumps(data, indent = 4)
