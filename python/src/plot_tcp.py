from argparse import ArgumentParser
import chart_studio.plotly as py
import plotly.graph_objects as go
import plotly.io as pio
import json
import csv

def parse_data(tcp_loc, test_num):
	
	data = {}
	# Replace 58 with the actual number of hops to remote data center
	hops = [2, 4, 6, 9, 9]

	count = 0
	data["tcp_send"] = []
	data["tcp_receive"] = []
	tcp = open(tcp_loc, "r")
	for line in tcp.readlines():
		fields = list(filter(None, line.strip().split(" ")))
		if len(fields) == 0:
			continue
		if fields[len(fields) - 1] == "sender":
			entry = {"test_id": test_num, "transferred": float(fields[4]), "bandwidth": float(fields[6])}
			if count/2 < len(hops):
				entry["hops"] = hops[count>>1]
			else:
				entry["hops"] = hops[len(hops)]
			data["tcp_send"].append(entry)
			count += 1
		elif fields[len(fields) - 1] == "receiver":
			entry = {"test_id": test_num, "transferred": float(fields[4]), "bandwidth": float(fields[6])}
			if count/2 < len(hops):
				entry["hops"] = hops[count>>1]
			else:
				entry["hops"] = hops[len(hops)]
			data["tcp_receive"].append(entry)
			count += 1
		elif fields[1] == "error":
			count += 2
			print("Error encountered in file " + tcp_loc)
	return data

parser = ArgumentParser("Parse data from tcp output files")
parser.add_argument("file")
parser.add_argument("title")


args = parser.parse_args()
loc = args.file
Title = args.title
data = []
for i in range(1, 21):
	data.append(parse_data(loc + "/run" + str(i) + "/tcp_test_no_load.out", i))
xsender = []
ysender = []
xreceiver = []
yreceiver = []
for result in data:
	for point in result["tcp_send"]:
		xsender.append(point["hops"])
		ysender.append(point["bandwidth"])
	for point in result["tcp_receive"]:
		xreceiver.append(point["hops"])
		yreceiver.append(point["bandwidth"])
plots = []
plots.append(go.Scatter(x=xsender, y=ysender, mode = "markers", name = "Sender Bandwidth"))
plots.append(go.Scatter(x=xreceiver, y=yreceiver, mode = "markers", name = "Receiver Bandwidth"))
layout = go.Layout(title = Title,
					xaxis = {"title" : "Number of Hops", "ticklen" : 1},
					yaxis = {"title" : "Bandwidth in MBps", "ticklen" : 0.1})
pio.write_html(go.Figure(data = plots, layout = layout),
				file = loc + "/tcp_plot.html", auto_open=True)

output = open(loc + "/tcp_data.csv", 'w')
writer = csv.writer(output)
writer.writerow(["test_id", "number_hops", "bytes_transferred_gb", "throughput_mbps"])

for result in data:
	for i in range(len(result["tcp_send"])):
		tcp_send = result["tcp_send"][i]
		writer.writerow([tcp_send["test_id"], tcp_send["hops"], tcp_send["transferred"],
							tcp_send["bandwidth"]])



