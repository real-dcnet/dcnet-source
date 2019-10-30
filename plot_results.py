from argparse import ArgumentParser
import plotly.express as px
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
	count = 0
	data["tcp_send"] = []
	data["tcp_receive"] = []
	tcp = open(args.tcp, "r")
	for line in tcp.readlines():
		fields = filter(None, line.strip().split(" "))
		if len(fields) == 0:
			continue
		if fields[len(fields) - 1] == "sender":
			entry = {"transferred": float(fields[4]), "bandwidth": float(fields[6])}
			if count/2 < len(hops):
				entry["hops"] = hops[count/2]
			else:
				entry["hops"] = hops[len(hops)]
			data["tcp_send"].append(entry)
			count += 1
		elif fields[len(fields) - 1] == "receiver":
			entry = {"transferred": float(fields[4]), "bandwidth": float(fields[6])}
			if count/2 < len(hops):
				entry["hops"] = hops[count/2]
			else:
				entry["hops"] = hops[len(hops)]
			data["tcp_receive"].append(entry)
			count += 1

init_ping = data["steady_ping"]
xdat = []
ydat = []
for point in init_ping:
	xdat.append(point["hops"])
	ydat.append(point["avg"])
fig = px.scatter(x=xdat, y=ydat)
fig.show()
