from argparse import ArgumentParser
import chart_studio.plotly as py
import plotly.graph_objects as go
import plotly.io as pio
import json
import csv

def parse_data(hv_loc, vm_loc, hops, test_num):
	
	data = {}

	flag = 0
	data["hv_ping_first"] = []
	data["hv_ping_second"] = []
	data["vm_ping_first"] = []
	data["vm_ping_second"] = []
	hv_file = open(hv_loc, "r")
	entry = {}
	for line in hv_file.readlines():
		fields = line.split(" ")
		if len(fields) == 0:
			continue
		if fields[0] == "rtt":
			stats = fields[3].split("/")
			entry["test_id"] = test_num
			entry["hops"] = hops
			entry["min"] = float(stats[0])
			entry["avg"] = float(stats[1])
			entry["max"] = float(stats[2])
			entry["dev"] = float(stats[3])
			if flag == 0:
				data["hv_ping_first"] = entry
			else:
				data["hv_ping_second"] = entry
			flag = 1 - flag
			entry = {}
		elif len(fields) > 3 and fields[2] == "transmitted,":
			entry["loss"] = int(fields[0]) - int(fields[3])
		elif len(fields) > 1 and fields[1] == "error":
			flag = 1 - flag
			entry = {}
			print("Error encountered in file " + hv_loc)
	hv_file.close()
	vm_file = open(vm_loc, "r")
	entry = {}
	for line in vm_file.readlines():
		fields = line.split(" ")
		if len(fields) == 0:
			continue
		if fields[0] == "rtt":
			stats = fields[3].split("/")
			entry["test_id"] = test_num
			entry["hops"] = hops
			entry["min"] = float(stats[0])
			entry["avg"] = float(stats[1])
			entry["max"] = float(stats[2])
			entry["dev"] = float(stats[3])
			if flag == 0:
				data["vm_ping_first"] = entry
			else:
				data["vm_ping_second"] = entry
			flag = 1 - flag
			entry = {}
		elif len(fields) > 3 and fields[2] == "transmitted,":
			entry["loss"] = int(fields[0]) - int(fields[3])
		elif len(fields) > 1 and fields[1] == "error":
			flag = 1 - flag
			entry = {}
			print("Error encountered in file " + vm_loc)
	vm_file.close()
	return data

parser = ArgumentParser("Parse data from ping output files")
parser.add_argument("file")

args = parser.parse_args()
loc = args.file
data = {"lf5-lf5": [], "lf5-lf6": [], "lf5-lf3": [], "lf5-lf8": []}#, "lf5-lf14": []}
for i in range(1, 11):
	dir_path = loc + "/lf5-lf5/run" + str(i) + "/"
	data["lf5-lf5"].append(parse_data(dir_path + "migrate_test_hv.out", dir_path + "migrate_test_vm.out", 2, i))
	dir_path = loc + "/lf5-lf6/run" + str(i) + "/"
	data["lf5-lf6"].append(parse_data(dir_path + "migrate_test_hv.out", dir_path + "migrate_test_vm.out", 4, i))
	dir_path = loc + "/lf5-lf3/run" + str(i) + "/"
	data["lf5-lf3"].append(parse_data(dir_path + "migrate_test_hv.out", dir_path + "migrate_test_vm.out", 6, i))
	dir_path = loc + "/lf5-lf8/run" + str(i) + "/"
	data["lf5-lf8"].append(parse_data(dir_path + "migrate_test_hv.out", dir_path + "migrate_test_vm.out", 9, i))
#	dir_path = loc + "/lf5-lf14/run" + str(i) + "/"
#	data["lf5-lf14"].append(parse_data(dir_path + "migrate_test_hv.out", dir_path + "migrate_test_vm.out", 9, i))
xvals = []
yhvdrop1 = []
yhvdrop2 = []
yvmdrop1 = []
yvmdrop2 = []
yhvmax1 = []
yhvmax2 = []
yvmmax1 = []
yvmmax2 = []
for result in data:
	for run in data[result]:
		point = run["hv_ping_first"]
		if point:
			xvals.append(point["hops"])
			yhvdrop1.append(point["loss"])
			yhvmax1.append(point["max"])

		point = run["hv_ping_second"]
		if point:
			yhvdrop2.append(point["loss"])
			yhvmax2.append(point["max"])

		point = run["vm_ping_first"]
		if point:
			yvmdrop1.append(point["loss"])
			yvmmax1.append(point["max"])

		point = run["vm_ping_second"]
		if point:
			yvmdrop2.append(point["loss"])
			yvmmax2.append(point["max"])
plots = []
plots.append(go.Scatter(x=xvals, y=yhvdrop1, mode = "markers", name = "Hypervisor Packets Dropped (Pass 1)"))
plots.append(go.Scatter(x=xvals, y=yhvdrop2, mode = "markers", name = "Hypervisor Packets Dropped (Pass 2)"))
plots.append(go.Scatter(x=xvals, y=yvmdrop1, mode = "markers", name = "VM Packets Dropped (Pass 1)"))
plots.append(go.Scatter(x=xvals, y=yvmdrop2, mode = "markers", name = "VM Packets Dropped (Pass 2)"))
layout = go.Layout(title = "Packet Drop vs. Number of Hops",
					xaxis = {"title" : "Number of Hops", "ticklen" : 1},
					yaxis = {"title" : "Packets Dropped", "ticklen" : 1})
pio.write_html(go.Figure(data = plots, layout = layout),
				file = loc + "/vm_drops_plot.html", auto_open=True)

plots = []
plots.append(go.Scatter(x=xvals, y=yhvmax1, mode = "markers", name = "Hypervisor Max Ping (Pass 1)"))
plots.append(go.Scatter(x=xvals, y=yhvmax2, mode = "markers", name = "Hypervisor Max Ping (Pass 2)"))
plots.append(go.Scatter(x=xvals, y=yvmmax1, mode = "markers", name = "VM Max Ping (Pass 1)"))
plots.append(go.Scatter(x=xvals, y=yvmmax2, mode = "markers", name = "VM Max Ping (Pass 2)"))
layout = go.Layout(title = "Maximum Ping Delay vs. Number of Hops",
					xaxis = {"title" : "Number of Hops", "ticklen" : 1},
					yaxis = {"title" : "Ping Delay (ms)", "ticklen" : 0.1})
pio.write_html(go.Figure(data = plots, layout = layout),
				file = loc + "/vm_ping_plot.html", auto_open=True)

output = open(loc + "/vm_data.csv", 'w')
writer = csv.writer(output)
writer.writerow(["test_id", "number_hops", "pass_number", "hv_ping_max", "hv_ping_min", 
					"hv_ping_avg", "hv_ping_dev", "hv_drops", "vm_ping_max",
					"vm_ping_min", "vm_ping_avg", "vm_ping_dev", "vm_drops"])

for result in data:
	for i in range(len(data[result])):
		hv_pass = data[result][i]["hv_ping_first"]
		vm_pass = data[result][i]["vm_ping_first"]
		if hv_pass and vm_pass:
			writer.writerow([hv_pass["test_id"], hv_pass["hops"], 1, hv_pass["max"], hv_pass["min"],
							hv_pass["avg"], hv_pass["dev"], hv_pass["loss"], vm_pass["max"],
							vm_pass["min"], vm_pass["avg"], vm_pass["dev"]])
		hv_pass = data[result][i]["hv_ping_second"]
		vm_pass = data[result][i]["vm_ping_second"]
		if hv_pass and vm_pass:
			writer.writerow([hv_pass["test_id"], hv_pass["hops"], 2, hv_pass["max"], hv_pass["min"],
							hv_pass["avg"], hv_pass["dev"], hv_pass["loss"], vm_pass["max"],
							vm_pass["min"], vm_pass["avg"], vm_pass["dev"], vm_pass["loss"]])

