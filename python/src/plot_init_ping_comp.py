from argparse import ArgumentParser
import chart_studio.plotly as py
import plotly.graph_objects as go
import plotly.io as pio
import json
import csv

def parse_data(ping_loc, test_num):
	
	data = {}
	# Replace 58 with the actual number of hops to remote data center
	hops = [2, 4, 6, 9, 9]
	
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
	return data

parser = ArgumentParser("Parse data from ping output files")
parser.add_argument("file1")
parser.add_argument("file2")

args = parser.parse_args()
loc = args.file1
data = []
for i in range(1, 9):
	data.append(parse_data(loc + "/run" + str(i) + "/ping_test_no_load.out", i))
plots = []
xinit = []
yinit = []
for result in data:
	for point in result["initial_ping"]:
		xinit.append(point["hops"])
		yinit.append(point["max"])
plots.append(go.Scatter(x=xinit, y=yinit, mode = "markers", name = "DCnet Initial Ping"))

loc = args.file2
data = []
for i in range(1, 9):
	data.append(parse_data(loc + "/run" + str(i) + "/ping_test_no_load.out", i))
xinit = []
yinit = []
for result in data:
	for point in result["initial_ping"]:
		xinit.append(point["hops"])
		yinit.append(point["max"])
plots.append(go.Scatter(x=xinit, y=yinit, mode = "markers", name = "Reactive Initial Ping"))

layout = go.Layout(title = "Ping Delay vs. Number of Hops",
					xaxis = {"title" : "Number of Hops", "ticklen" : 1},
					yaxis = {"title" : "Delay in Milliseconds", "ticklen" : 0.1})

pio.write_html(go.Figure(data = plots, layout = layout),
				file = loc + "../plots/ping_comparison_init.html", auto_open=True)
