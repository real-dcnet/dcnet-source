from argparse import ArgumentParser
import chart_studio.plotly as py
import plotly.graph_objects as go
import plotly.io as pio
import json
import csv

def parse_data(ping_loc, tcp_loc, test_num):
	
	data = {}
	# Replace 58 with the actual number of hops to remote data center
	hops = [2, 4, 6, 9, 9]
	
	if ping_loc:
		count = 0
		data["initial_ping"] = []
		data["steady_ping"] = []
		ping = open(ping_loc, "r")
		for line in ping.readlines():
			fields = line.split(" ")
			if len(fields) == 0:
				continue
			if fields[0] == "rtt":
				stats = fields[3].split("/")
				entry = {"test_id": test_num, "min": float(stats[0]), "avg": float(stats[1]),
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
			elif len(fields) > 1 and fields[1] == "error":
				count += 1
				print("Error encountered in file " + ping_loc)
		ping.close()
	
	if tcp_loc:
		count = 0
		data["tcp_send"] = []
		data["tcp_receive"] = []
		tcp = open(tcp_loc, "r")
		for line in tcp.readlines():
			fields = filter(None, line.strip().split(" "))
			if len(fields) == 0:
				continue
			if fields[len(fields) - 1] == "sender":
				entry = {"test_id": test_num, "transferred": float(fields[4]), "bandwidth": float(fields[6])}
				if count/2 < len(hops):
					entry["hops"] = hops[count/2]
				else:
					entry["hops"] = hops[len(hops)]
				data["tcp_send"].append(entry)
				count += 1
			elif fields[len(fields) - 1] == "receiver":
				entry = {"test_id": test_num, "transferred": float(fields[4]), "bandwidth": float(fields[6])}
				if count/2 < len(hops):
					entry["hops"] = hops[count/2]
				else:
					entry["hops"] = hops[len(hops)]
				data["tcp_receive"].append(entry)
				count += 1
			elif fields[1] == "error":
				count += 2
				print("Error encountered in file " + tcp_loc)
	return data

parser = ArgumentParser("Parse data from ping and tcp output files")
parser.add_argument("--path", required = True, type = str,
					help = "Location of test output for all runs")

args = parser.parse_args()
loc = args.path
data = []
for i in range(1, 21):
	data.append(parse_data(loc + "/run" + str(i) + "/ping_test.out",
							loc + "/run" + str(i) + "/tcp_test.out", i))
xinit = []
yinit = []
xsteady = []
ysteady = []
xmin = []
ymin = []
xsender = []
ysender = []
xreceiver = []
yreceiver = []
for result in data:
	for point in result["initial_ping"]:
		xinit.append(point["hops"])
		yinit.append(point["max"])
	for point in result["steady_ping"]:
		xsteady.append(point["hops"])
		ysteady.append(point["avg"])
		xmin.append(point["hops"])
		ymin.append(point["min"])
	for point in result["tcp_send"]:
		xsender.append(point["hops"])
		ysender.append(point["bandwidth"])
	for point in result["tcp_receive"]:
		xreceiver.append(point["hops"])
		yreceiver.append(point["bandwidth"])
plots = []
plots.append(go.Scatter(x=xinit, y=yinit, mode = "markers", name = "Initial Ping"))
plots.append(go.Scatter(x=xsteady, y=ysteady, mode = "markers", name = "Average Ping"))
plots.append(go.Scatter(x=xmin, y=ymin, mode = "markers", name = "Minimum Ping"))
layout = go.Layout(title = "Ping Delay vs. Number of Hops",
					xaxis = {"title" : "Number of Hops", "ticklen" : 1},
					yaxis = {"title" : "Delay in Milliseconds", "ticklen" : 0.1})
pio.write_html(go.Figure(data = plots, layout = layout),
				file = loc + "/ping_plot.html", auto_open=True)
plots = []
plots.append(go.Scatter(x=xsender, y=ysender, mode = "markers", name = "Sender Bandwidth"))
plots.append(go.Scatter(x=xreceiver, y=yreceiver, mode = "markers", name = "Receiver Bandwidth"))
layout = go.Layout(title = "TCP Bandwidth vs. Number of Hops",
					xaxis = {"title" : "Number of Hops", "ticklen" : 1},
					yaxis = {"title" : "Bandwidth in MBps", "ticklen" : 0.1})
pio.write_html(go.Figure(data = plots, layout = layout),
				file = loc + "/tcp_plot.html", auto_open=True)

output = open(loc + "/ping_data.csv", 'w')
writer = csv.writer(output)
writer.writerow(["Test ID", "Number Hops", "Initial Ping (Max)", "Initial Ping (Min)", "Initial Ping (Avg)",
					"Initial Ping (Dev)", "Steady Ping (Max)", "Steady Ping (Min)", "Steady Ping (Avg)",
					"Steady Ping (Dev)"])

for result in data:
	for i in range(len(result["initial_ping"])):
		ping_init = result["initial_ping"][i]
		ping_steady = result["steady_ping"][i]
		writer.writerow([ping_steady["test_id"], ping_steady["hops"], ping_init["max"], ping_init["min"],
							ping_init["avg"], ping_init["dev"], ping_steady["max"],
							ping_steady["min"], ping_steady["avg"], ping_steady["dev"]])

output = open(loc + "/tcp_data.csv", 'w')
writer = csv.writer(output)
writer.writerow(["Test ID", "Number Hops", "Bytes Transferred (GB)", "Throughput (Mbps)"])

for result in data:
	for i in range(len(result["tcp_send"])):
		tcp_send = result["tcp_send"][i]
		writer.writerow([tcp_send["test_id"], tcp_send["hops"], tcp_send["transferred"],
							tcp_send["bandwidth"]])



