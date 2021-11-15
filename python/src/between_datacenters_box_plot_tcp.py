#
#   Analyzes results from a trial between two datacenters
#   Repeats for as many receiver logs as are specified
#

from argparse import ArgumentParser
import chart_studio.plotly as py
import plotly.graph_objects as go
import plotly.io as pio
import json
import csv

NUM_TRIALS_TO_PLOT = 30
NUM_TRIALS = 33
NUM_RECEIVERS = 1
hops = [2, 4, 6, 9, 9]

def parse_data(tcp_loc, test_num):
	
	data = {}
	# Replace 58 with the actual number of hops to remote data center
	# hops = [2, 4, 6, 9, 9]

        # Remove the first two and last two seconds of the test
    
	count = 0
	data["tcp_receive"] = []
	tcp = open(tcp_loc, "r")
	for line in tcp.readlines():
		fields = list(filter(None, line.strip().split(" ")))
		if len(fields) < 2:
			continue

                if fields[1] == "error":
                        # iperf error in this test. Discard entire trial
                        count += 2
                        print("Error encountered in file " + tcp_loc)
                        break


                if len(fields) < 8:
                    # This isn't the line you're looking for
                    continue

                # Convert bandwidth to Mbps if necessary
                bw = float(fields[6])
                if (fields[7] == "Kbits/sec"):
                            bw = bw / 1024.0

                if fields[2] == "0.00-1.00":
                    entry = {"second":1, "bandwdith":bw}
                elif fields[2] == "1.00-2.00":
                    entry = {"second":2, "bandwdith":bw}
                elif fields[2] == "2.00-3.00":
                    entry = {"second":3, "bandwdith":bw}
                elif fields[2] == "3.00-4.00":
                    entry = {"second":4, "bandwdith":bw}
                elif fields[2] == "4.00-5.00":
                    entry = {"second":5, "bandwdith":bw}
                elif fields[2] == "5.00-6.00":
                    entry = {"second":6, "bandwdith":bw}
                elif fields[2] == "6.00-7.00":
                    entry = {"second":7, "bandwdith":bw}
                elif fields[2] == "7.00-8.00":
                    entry = {"second":8, "bandwdith":bw}
                elif fields[2] == "8.00-9.00":
                    entry = {"second":9, "bandwdith":bw}
                elif fields[2] == "9.00-10.00":
                    entry = {"second":10, "bandwdith":bw}
                data["tcp_receive"].append(entry)

	return data

parser = ArgumentParser("Parse data from tcp output files")
parser.add_argument("file")
parser.add_argument("title")
parser.add_argument("num_trials")
parser.add_argument("num_receivers")

args = parser.parse_args()
loc = args.file
Title = args.title
trials = args.num_trials
if (trials.isdigit):
    NUM_TRAILS = int(trials)
else:
    print("ERROR: Number of trials must be an integer")
num_receivers = args.num_receivers
if (num_receivers.isdigit):
    NUM_RECEIVERS = int(num_receivers)
else:
    print("ERROR: Number of receivers must be an integer")

data = []

# Gather data from all of the tcp_receive_no_load.out files
for l in range(1, NUM_RECEIVERS + 1):
    receiver_data = []
    file_name = ""
    if (l == 1):
        file_name = "/tcp_receive_no_load.out"
    else:
        file_name = "/tcp_receive_no_load" + l + ".out"
    for i in range(1, NUM_TRIALS + 1):
	receiver_data.append(parse_data(loc + "/run" + str(i) + file_name, i))
    data.append("receiver":l, "data":receiver_data)




if len(data) > NUM_TRIALS_TO_PLOT:
    print("ERROR: Not enough trials succeeded to plot (30 required)")
    exit()

avg_bandwidth_per_second_for_each_receiver = []
receiver_fig = go.Figure()
for receiver in data:
    x_vals = []
    y_vals = []
    avg_receiver_bandwidth = {}
    counter = 1
    for result in data.data:
        if counter <= NUM_TRIALS_TO_PLOT:   # Only consider the first 30 results
            counter += 1
            for point in result["tcp_receive"]:
		x_vals.append(point["second"])
                
                if point["second"] in avg_receiver_bandwidth:
                    avg_receiver_bandwidth[point["second"]] += float(point["bandwidth"])
                else:
                    avg_receiver_bandwidth[point["second"]] = float(point["bandwidth"])
    for key in avg_receiver_bandwidth:
        # Calculate average bandwidth for each second of the test for this host
        avg_receiver_bandwidth[key] /= NUM_TRIALS_TO_PLOT
        y_vals.append(avg_receiver_bandwidth[key])
    avg_bandwidth_per_second_for_each_receiver.append({"receiver":receiver, "data":avg_receiver_bandwidth})
    
    # Add line plot to figure that contians multiple line plots
    receiver_fig.add_trace(go.Scatter(y=y_vals, x=x_vals, mode='line+markers', name=str(receiver)))
    

# Can repeat for the other hops
pio.write_html(go.Figure(data = receiver_fig, layout = layout), file = loc + "/receiver_line_plot_tcp.html", auto_open=False)
#pio.write_html(go.Figure(data = plots, layout = layout),
#				file = loc + "/tcp_plot.html", auto_open=True)

# TODO: See how to write CSV data by looking at the other plotting programs I've written
print("Finished.")


