from argparse import ArgumentParser
import chart_studio.plotly as py
import plotly.graph_objects as go
import plotly.io as pio
import json

def parse_data(ping_loc, tcp_loc):
	
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
	return data
	init_ping = data["steady_ping"]
	xdat = []
	ydat = []
	for point in init_ping:
		xdat.append(point["hops"])
		ydat.append(point["avg"])
	fig = px.scatter(x=xdat, y=ydat)
	fig.show()


parser = ArgumentParser("Parse data from ping and tcp output files")
parser.add_argument("--path", required = True, type = str,
					help = "Location of test output for all runs")

args = parser.parse_args()
loc = args.path
data = []
for i in range(1, 10):
	data.append(parse_data(loc + "/run" + str(i) + "/ping_test_no_load.out",
							loc + "/run" + str(i) + "/tcp_test_with_load.out"))
xinit = []
yinit = []
xsteady = []
ysteady = []
xsender = []
ysender = []
xreceiver = []
yreceiver = []
for result in data:
	for point in result["initial_ping"]:
		xinit.append(point["hops"])
		yinit.append(point["avg"])
	for point in result["steady_ping"]:
		xsteady.append(point["hops"])
		ysteady.append(point["avg"])
	for point in result["tcp_send"]:
		xsender.append(point["hops"])
		ysender.append(point["bandwidth"])
	for point in result["tcp_receive"]:
		xreceiver.append(point["hops"])
		yreceiver.append(point["bandwidth"])
plots = []
plots.append(go.Scatter(x=xinit, y=yinit, mode = "markers", name = "Initial Ping"))
plots.append(go.Scatter(x=xsteady, y=ysteady, mode = "markers", name = "Average Ping"))
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
#fig = px.scatter(x=xinit, y=yinit)
#fig.show()
#fig = px.scatter(x=xsteady, y=ysteady)
#fig.show()
#fig = px.scatter(x=xsender, y=ysender)
#fig.show()
#fig = px.scatter(x=xreceiver, y=yreceiver)
#fig.show()

