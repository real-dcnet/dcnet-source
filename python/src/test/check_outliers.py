from argparse import ArgumentParser
from scipy import stats
import numpy as np
import json
import csv

def parse_data(ping_loc, test_num):
	
    data = {}
	# Replace 58 with the actual number of hops to remote data center
    hops = [2, 4, 6, 9, 10]

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
            entry = {"test_id": test_num, "min": float(stats[0]), "avg": float(stats[1]), "max": float(stats[2]), "dev": float(stats[3])}
            if count/2 < len(hops):
                entry["hops"] = hops[int(count/2)]
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

def detect_outliers(loc, DATA, ping, test, threshold=3):
    #check outliers for initial ping data
    # print(ping)
    outliers = []
    for hops in DATA[ping]:
        hops_data = DATA[ping][hops][0]
        hops_z_score = np.abs(stats.zscore(hops_data))
        hops_outliers = np.where(hops_z_score > threshold)[0]
        if hops_outliers.size > 0:
            # print("Outlier found in for test {}".format(test))
            for outlier in hops_outliers:
                # print(loc + "/run"+ str(DATA[ping][hops][1][outlier]) + "/ping_test_no_load.out")
                outliers.append(loc + "/run"+ str(DATA[ping][hops][1][outlier]))
            # print("\n")
        test +=2

    return outliers

def main():
    parser = ArgumentParser("Parse data from ping output files")
    parser.add_argument("file")
    parser.add_argument("trials")

    args = parser.parse_args()
    files = args.file
    loc = files.split("/")[0]
    trials = int(args.trials)+1
    ping_avg_list = []
    ping_max_list = []
    data = []
    for i in range(1, trials):
        data.append(parse_data(loc + "/run"+ str(i) + "/ping_test_no_load.out", i))

    DATA = {"initial_ping":{"2": [[],[]], "4":[[],[]], "6":[[],[]], "9":[[],[]], "10":[[],[]]}, "steady_ping": {"2": [[],[]], "4":[[],[]], "6":[[],[]], "9":[[],[]], "10":[[],[]]} }

    for result in data:
        for point in result["initial_ping"]:
            hops = str(point["hops"])
            DATA["initial_ping"][hops][0].append(point["avg"])
            DATA["initial_ping"][hops][1].append(point["test_id"])
        for point in result["steady_ping"]:
            hops = str(point["hops"])
            DATA["steady_ping"][hops][0].append(point["avg"])
            DATA["steady_ping"][hops][1].append(point["test_id"])

    threshold=3
    init_outliers = detect_outliers(loc, DATA, "initial_ping",1,threshold)
    steady_outliers = detect_outliers(loc, DATA, "steady_ping",2, threshold)
    total_outliers = []
    total_outliers.extend(init_outliers)
    total_outliers.extend(steady_outliers)
    total_outliers = set(total_outliers)
    for file in total_outliers:
        print(file)

    # hops_np = np.array(DATA["initial_ping"]["9"][0])
    # print("numpy array", hops_np)
    # z = np.abs(stats.zscore(hops_np))
    # print("z-scores", z)
    # threshold = 3
    # outliers = np.where(z > 1)[0]
    # for i in outliers:
    #     print(i)
    # avgdata = data[0]
    # maxdata = data[1]
    # ping_avg_list.append(avgdata)
    # ping_max_list.append(maxdata)

    # ping_avg_np = np.array(ping_avg_list)
    # ping_max_np = np.array(ping_max_list)
    # print(ping_avg_np)
    # print(ping_max_np)

if __name__ == "__main__":
    main()
