#
# Creates boxplot for bidirectional traffic between hosts
#

from argparse import ArgumentParser
import chart_studio.plotly as py
import plotly.graph_objects as go
import plotly.io as pio
import json
import csv

NUM_TRIALS_TO_PLOT = 30
NUM_TRIALS = 33
hops = [2, 4, 6, 9]#, 9]

def parse_data(tcp_loc, test_num):
	
	data = {}

	count = 0
	
        data["host1_send"] = []
        data["host1_receive"] = []
        
        data["host2_send"] = []
        data["host2_receive"] = []

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

                        if "[TX-C]" in fields[1]:
                            # Host 1 is transmitting
                            print(entry)
                            data["host1_send"].append(entry)
                        elif "[RX-C]" in fields[1]:
                            # Host 1 is receiving
                            data["host2_send"].append(entry)
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
                        if "[TX-C]" in fields[1]:
                            # Host 1 is transmitting
                            data["host2_receive"].append(entry)
                        elif "[RX-C]" in fields[1]:
                            # Host 1 is receiving
                            data["host1_receive"].append(entry)
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

'''
xsender = []
ysender = []
y_sender_hops = {}  # Dictionary of all bandwidth points for each hop
xreceiver = []
yreceiver = []
y_receiver_hops = {}  # Dictionary of all bandwidth points for each hop
avg_sender_bandwidth = {}
avg_receiver_bandwidth = {}
'''

host1_send_x = []   # x values (hops) for host 1 sending
host1_send_y = []   # y values (bandwidth) for host 2 sending

host2_send_x = []   # x values (hops) for host 2 sending
host2_send_y = []   # y values (bandwidth) for host 2 sending

host1_send_y_hops = {}  # Dictionary of all bandwidth points for each hop for host 1 sending
host2_send_y_hops = {}  # Dictionary of all bandwidth points for each hop for host 2 sending


host1_receive_x = []    # x values (hops) for host 1 receiving
host1_receive_y = []    # y values (bandwidth) for host 1 receiving

host2_receive_x = []    # x values (hops) for host 2 receiving
host2_receive_y = []    # y values (bandwidth) for host 2 receiving

host1_receive_y_hops = {}   # Dictionary of all bandwidth points for each hop for host 1 receiving
host2_receive_y_hops = {}   # Dictionary of all bandwidth points for each hop for host 2 receiving


host1_avg_sending_bandwidth = {}
host1_avg_receiving_bandwidth = {}
host2_avg_sending_bandwidth = {}
host2_avg_receiving_bandwidth = {}

if len(data) < NUM_TRIALS_TO_PLOT:
    print("ERROR: Not enough trials succeeded to plot (30 required)")
    print("       Only " + str(len(data)) + " trials succeeded.")
    exit()

counter = 1
for result in data:
    if counter <= NUM_TRIALS_TO_PLOT:   # Only consider the first 30 results
        counter += 1
        
        for point in result["host1_send"]:
            host1_send_x.append(point["hops"])
            host1_send_y.append(point["bandwidth"])
            if point["hops"] in host1_avg_sending_bandwidth:
                host1_avg_sending_bandwidth[point["hops"]] += float(point["bandwidth"])
            else:
                host1_avg_sending_bandwidth[point["hops"]] = float(point["bandwidth"])
            if point["hops"] in host1_send_y_hops:
                host1_send_y_hops[point["hops"]].append(point["bandwidth"])
            else:
                host1_send_y_hops[point["hops"]] = [point["bandwidth"]]

        for point in result["host1_receive"]:
            host1_receive_x.append(point["hops"])
            host1_receive_y.append(point["bandwidth"])
            if point["hops"] in host1_avg_receiving_bandwidth:
                host1_avg_receiving_bandwidth[point["hops"]] += float(point["bandwidth"])
            else:
                host1_avg_receiving_bandwidth[point["hops"]] = float(point["bandwidth"])
            if point["hops"] in host1_receive_y_hops:
                host1_receive_y_hops[point["hops"]].append(point["bandwidth"])
            else:
                host1_receive_y_hops[point["hops"]] = [point["bandwidth"]]
        
        for point in result["host2_send"]:
            host2_send_x.append(point["hops"])
            host2_send_y.append(point["bandwidth"])
            if point["hops"] in host2_avg_sending_bandwidth:
                host2_avg_sending_bandwidth[point["hops"]] += float(point["bandwidth"])
            else:
                host2_avg_sending_bandwidth[point["hops"]] = float(point["bandwidth"])
            if point["hops"] in host2_send_y_hops:
                host2_send_y_hops[point["hops"]].append(point["bandwidth"])
            else:
                host2_send_y_hops[point["hops"]] = [point["bandwidth"]]

        for point in result["host2_receive"]:
            host2_receive_x.append(point["hops"])
            host2_receive_y.append(point["bandwidth"])
            if point["hops"] in host2_avg_receiving_bandwidth:
                host2_avg_receiving_bandwidth[point["hops"]] += float(point["bandwidth"])
            else:
                host2_avg_receiving_bandwidth[point["hops"]] = float(point["bandwidth"])
            if point["hops"] in host2_receive_y_hops:
                host2_receive_y_hops[point["hops"]].append(point["bandwidth"])
            else:
                host2_receive_y_hops[point["hops"]] = [point["bandwidth"]]
        
        ''' 
        for point in result["tcp_send"]:
		xsender.append(point["hops"])
		ysender.append(point["bandwidth"])
                if point["hops"] in avg_sender_bandwidth:
                    avg_sender_bandwidth[point["hops"]] += float(point["bandwidth"])
	        else:
                    avg_sender_bandwidth[point["hops"]] = float(point["bandwidth"])
                if point["hops"] in y_sender_hops:
                    y_sender_hops[point["hops"]].append(point["bandwidth"])
                else:
                    y_sender_hops[point["hops"]] = [point["bandwidth"]]
        for point in result["tcp_receive"]:
		xreceiver.append(point["hops"])
		yreceiver.append(point["bandwidth"])
                if point["hops"] in avg_receiver_bandwidth:
                    avg_receiver_bandwidth[point["hops"]] += float(point["bandwidth"])
                else:
                    avg_receiver_bandwidth[point["hops"]] = float(point["bandwidth"])
                if point["hops"] in y_receiver_hops:
                    y_receiver_hops[point["hops"]].append(point["bandwidth"])
                else:
                    y_receiver_hops[point["hops"]] = [point["bandwidth"]]
        '''

# Calculate average bandwidth among the tests per hop
avg_host1_sender_hops_array = []
avg_host2_sender_hops_array = []
# TODO: RENAME THESE TO INCLUDE 'SORTED' INSTEAD OF 'HOPS'
avg_host1_receiver_hops_array = []
avg_host2_receiver_hops_array = []

for hop in hops:
    host1_avg_sending_bandwidth[hop] /= NUM_TRIALS_TO_PLOT
    host1_avg_receiving_bandwidth[hop] /= NUM_TRIALS_TO_PLOT
    avg_host1_sender_hops_array.append(host1_avg_sending_bandwidth[hop])
    avg_host1_receiver_hops_array.append(host1_avg_receiving_bandwidth[hop])
    
    host2_avg_sending_bandwidth[hop] /= NUM_TRIALS_TO_PLOT
    host2_avg_receiving_bandwidth[hop] /= NUM_TRIALS_TO_PLOT
    avg_host2_sender_hops_array.append(host2_avg_sending_bandwidth[hop])
    avg_host2_receiver_hops_array.append(host2_avg_receiving_bandwidth[hop])

print("Average host 1 sending")
print(avg_host1_sender_hops_array)
print("Average host 1 receiving")
print(avg_host1_receiver_hops_array)
print("Average host 2 sending")
print(avg_host2_sender_hops_array)
print("Average host 2 receiving")
print(avg_host2_receiver_hops_array)


'''
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
'''

plots = []
#plots.append(go.Scatter(x=xsender, y=ysender, mode = "markers", name = "Sender Bandwidth"))
#plots.append(go.Scatter(x=xreceiver, y=yreceiver, mode = "markers", name = "Receiver Bandwidth"))
plots.append(go.Scatter(x=hops, y=avg_host1_sender_hops_array, name="Host 1 Average Sending Bandwidth"))
plots.append(go.Scatter(x=hops, y=avg_host1_receiver_hops_array, name="Host 1 Average Receiving Bandwidth"))
plots.append(go.Scatter(x=hops, y=avg_host2_sender_hops_array, name="Host 2 Average Sending Bandwidth"))
plots.append(go.Scatter(x=hops, y=avg_host2_receiver_hops_array, name="Host 2 Average Receiving Bandwidth"))
layout = go.Layout(title = Title,
					xaxis = {"title" : "Number of Hops", "ticklen" : 1},
					yaxis = {"title" : "Bandwidth in MBps", "ticklen" : 0.1})
# TODO: MAKE ALL OF THE BELOW WORK FOR DOUBLE HOP
''''
sender_fig = go.Figure()
receiver_fig = go.Figure()
for hop in hops:
    sender_fig.add_trace(go.Box(y=y_sender_hops[hop], boxpoints="outliers", name=str(hop)))
    receiver_fig.add_trace(go.Box(y=y_receiver_hops[hop], boxpoints="outliers", name=str(hop)))
'''
host1_send_fig = go.Figure()
host1_receive_fig = go.Figure()
host2_send_fig = go.Figure()
host2_receive_fig = go.Figure()
for hop in hops:
    host1_send_fig.add_trace(go.Box(y=host1_send_y_hops[hop], boxpoints="outliers", name=str(hop)))
    host1_receive_fig.add_trace(go.Box(y=host1_receive_y_hops[hop], boxpoints="outliers", name=str(hop)))
    host2_send_fig.add_trace(go.Box(y=host2_send_y_hops[hop], boxpoints="outliers", name=str(hop)))
    host2_receive_fig.add_trace(go.Box(y=host2_receive_y_hops[hop], boxpoints="outliers", name=str(hop)))


# Can repeat for the other hops
pio.write_html(go.Figure(data = host1_send_fig, layout = layout), file = loc + "/host1_send_box_plot_tcp.html", auto_open=False)
pio.write_html(go.Figure(data = host1_receive_fig, layout = layout), file = loc + "/host1_receive_box_plot_tcp.html", auto_open=False)
pio.write_html(go.Figure(data = host2_send_fig, layout = layout), file = loc + "/host2_send_box_plot_tcp.html", auto_open=False)
pio.write_html(go.Figure(data = host2_receive_fig, layout = layout), file = loc + "/host2_receive_box_plot_tcp.html", auto_open=False)

output = open(loc + "/tcp_data.csv", 'w')
writer = csv.writer(output)
writer.writerow(["test_id", "number_hops", "bytes_transferred_gb", "throughput_mbps"])
'''
for result in data:
	for i in range(len(result["tcp_send"])):
		tcp_send = result["tcp_send"][i]
		writer.writerow([tcp_send["test_id"], tcp_send["hops"], tcp_send["transferred"],
							tcp_send["bandwidth"]])
'''
print("Finished.")


