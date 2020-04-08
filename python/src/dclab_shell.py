from cmd import Cmd
import json
import os

class DClabShell(Cmd):
    config = []
    config_loc = "../../config/dclab/test_config.json"

    def do_exit(self, inp):
        print("Exiting")
        return True

    def do_create(self, inp):
        self.config = []
        if inp:
            self.do_append(inp)

    def do_append(self, inp):
        if not inp:
            print("Error: append requires input")
            return
        parsed = inp.split(",")
        if parsed[0] == "linear":
            self.config.append({"type": parsed[0],
                                "length": int(parsed[1]),
                                "count": int(parsed[2])})

        if parsed[0] == "star":
            self.config.append({"type": parsed[0],
                                "points": int(parsed[1]),
                                "count": int(parsed[2])})

        if parsed[0] == "tree":
            self.config.append({"type": parsed[0],
                                "depth": int(parsed[1]),
                                "fanout": int(parsed[1]),
                                "count": int(parsed[2])})

    def do_write(self, inp):
        file = open(self.config_loc, "w+")
        file.write(json.dumps(self.config, indent = 4))

    def do_apply(self, inp):
        os.system("/opt/onos/bin/onos-app 127.0.0.1 uninstall org.onosproject.dclab")
        os.system("/opt/onos/bin/onos-app 127.0.0.1 reinstall! ~/dcnet-source/dclab/target/onos-app-dclab-2.1.0.oar ")

    def do_show(self, inp):
        print(json.dumps(self.config, indent = 4))

shell = DClabShell
file = open(shell.config_loc, "r")
shell.config = json.load(file)
DClabShell().cmdloop(shell)