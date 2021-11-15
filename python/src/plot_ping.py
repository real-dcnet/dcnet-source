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
				entry["hops"] = hops[count>>1]
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
parser.add_argument("file")
parser.add_argument("title")

args = parser.parse_args()
loc = args.file
Title = args.title
data = []
#for i in range(1, 21):
for i in range(1, 5):
    data.append(parse_data(loc + "/run" + str(i) + "/ping_test_no_load.out", i))
xinit = []
yinit = []
xsteady = []
ysteady = []
xmin = []
ymin = []
for result in data:
	for point in result["initial_ping"]:
		xinit.append(point["hops"])
		yinit.append(point["max"])
	for point in result["steady_ping"]:
		xsteady.append(point["hops"])
		ysteady.append(point["avg"])
		xmin.append(point["hops"])
		ymin.append(point["min"])
plots = []
plots.append(go.Scatter(x=xinit, y=yinit, mode = "markers", name = "Initial Ping"))
plots.append(go.Scatter(x=xsteady, y=ysteady, mode = "markers", name = "Average Ping"))
plots.append(go.Scatter(x=xmin, y=ymin, mode = "markers", name = "Minimum Ping"))
layout = go.Layout(title = Title,
					xaxis = {"title" : "Number of Hops", "ticklen" : 1},
					yaxis = {"title" : "Delay in Milliseconds", "ticklen" : 0.1})
pio.write_html(go.Figure(data = plots, layout = layout),
				file = loc + "/ping_plot.html", auto_open=True)

output = open(loc + "/ping_data.csv", 'w')
writer = csv.writer(output)
writer.writerow(["test_id", "number_hops", "initial_ping_max", "initial_ping_min", "initial_ping_avg",
					"initial_ping_dev", "steady_ping_max", "steady_ping_min", "steady_ping_avg",
					"steady_ping_dev"])

for result in data:
	for i in range(len(result["initial_ping"])):
		ping_init = result["initial_ping"][i]
		ping_steady = result["steady_ping"][i]
		writer.writerow([ping_steady["test_id"], ping_steady["hops"], ping_init["max"], ping_init["min"],
							ping_init["avg"], ping_init["dev"], ping_steady["max"],
							ping_steady["min"], ping_steady["avg"], ping_steady["dev"]])

