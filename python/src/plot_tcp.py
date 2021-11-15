from argparse import ArgumentParser
import chart_studio.plotly as py
import plotly.graph_objects as go
import plotly.io as pio
import json
import csv

NUM_TRIALS_TO_PLOT = 30
NUM_TRIALS = 30
hops = [2, 4, 6, 9, 9]

def parse_data(tcp_loc, test_num):
	
	data = {}
	# Replace 58 with the actual number of hops to remote data center
	# hops = [2, 4, 6, 9, 9]

	count = 0
	data["tcp_send"] = []
	data["tcp_receive"] = []
	tcp = open(tcp_loc, "r")
	for line in tcp.readlines():
		fields = list(filter(None, line.strip().split(" ")))
		if len(fields) == 0:
			continue
		if fields[len(fields) - 1] == "sender":
                        bw = float(fields[6])
                        if (fields[7] == "Kbits/sec"):
                            bw = bw / 1024.0
                        entry = {"test_id": test_num, "transferred": float(fields[4]), "bandwidth": bw}
			if count/2 < len(hops):
				entry["hops"] = hops[count>>1]
			else:
				entry["hops"] = hops[len(hops)]
			data["tcp_send"].append(entry)
			count += 1
		elif fields[len(fields) - 1] == "receiver":
			bw = float(fields[6])
                        if (fields[7] == "Kbits/sec"):
                            bw = bw / 1024.0
                        entry = {"test_id": test_num, "transferred": float(fields[4]), "bandwidth": bw}
			if count/2 < len(hops):
				entry["hops"] = hops[count>>1]
			else:
				entry["hops"] = hops[len(hops)]
			data["tcp_receive"].append(entry)
			count += 1
		elif fields[1] == "error":
			# iperf error in this test. Discard entire trial
                        count += 2
			print("Error encountered in file " + tcp_loc)
                        break
	return data

parser = ArgumentParser("Parse data from tcp output files")
parser.add_argument("file")
parser.add_argument("title")
parser.add_argument("num_trials")

args = parser.parse_args()
loc = args.file
Title = args.title
trials = args.num_trials
if (trials.isdigit):
    NUM_TRAILS = int(trials)
else:
    print("ERROR: Number of trials must be an integer")
data = []

for i in range(1, NUM_TRIALS + 1):
	data.append(parse_data(loc + "/run" + str(i) + "/tcp_test_no_load.out", i))
xsender = []
ysender = []
xreceiver = []
yreceiver = []
avg_sender_bandwidth = {}
avg_receiver_bandwidth = {}
if len(data) > NUM_TRIALS_TO_PLOT:
    print("ERROR: Not enough trials succeeded to plot (30 required)")
    exit()
counter = 1
for result in data:
    if counter <= NUM_TRIALS_TO_PLOT:   # Only consider the first 30 results
        counter += 1
        for point in result["tcp_send"]:
		xsender.append(point["hops"])
		ysender.append(point["bandwidth"])
                if point["hops"] in avg_sender_bandwidth:
                    avg_sender_bandwidth[point["hops"]] += float(point["bandwidth"])
	        else:
                    avg_sender_bandwidth[point["hops"]] = float(point["bandwidth"])
        for point in result["tcp_receive"]:
		xreceiver.append(point["hops"])
		yreceiver.append(point["bandwidth"])
                if point["hops"] in avg_receiver_bandwidth:
                    avg_receiver_bandwidth[point["hops"]] += float(point["bandwidth"])
                else:
                    avg_receiver_bandwidth[point["hops"]] = float(point["bandwidth"])

# Calculate average bandwidth among the tests per hop
avg_sender_hops_array = []
# TODO: RENAME THESE TO INCLUDE 'SORTED' INSTEAD OF 'HOPS'
avg_receiver_hops_array = []
for hop in hops:
    avg_sender_bandwidth[hop] /= NUM_TRIALS_TO_PLOT
    avg_receiver_bandwidth[hop] /= NUM_TRIALS_TO_PLOT
    avg_sender_hops_array.append(avg_sender_bandwidth[hop])
    avg_receiver_hops_array.append(avg_receiver_bandwidth[hop])
    if hop == 9:
        # We have two tests at 9 hops
        avg_sender_hops_array[3] /= 2
        avg_receiver_hops_array[3] /= 2
        break

plots = []
#plots.append(go.Scatter(x=xsender, y=ysender, mode = "markers", name = "Sender Bandwidth"))
#plots.append(go.Scatter(x=xreceiver, y=yreceiver, mode = "markers", name = "Receiver Bandwidth"))
plots.append(go.Scatter(x=hops, y=avg_sender_hops_array, name="Average Sender Bandwidth"))
plots.append(go.Scatter(x=hops, y=avg_receiver_hops_array, name="Average Receiver Bandwidth"))
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



