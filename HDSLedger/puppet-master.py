#!/usr/bin/env python

import os
import json
import sys
import signal


# Terminal Emulator used to spawn the processes
terminal = "kitty"

# Blockchain node configuration file name
server_configs = [
    "regular_config.json",
    "non_responsive_ibft_works_config.json",
    "non_responsive_ibft_fails_config.json",
    "fake_leader_C_P_config.json",
    "fake_commit_config.json",
    "fake_prepare_config.json",
    "fake_leader_PP_config.json",
    "leader_pretending_config.json",
    "fake_preprepare_config.json",
    "broadcast_fail_leader.json",
    "no_prepare_01.json",
    "no_commit_01.json",
    "sleep_servers_config.json"
]

client_configs = [
    "regular_config.json",
    "no_send_to_leader_config.json"
]


server_config = server_configs[0]

client_config = client_configs[0]

def quit_handler(*args):

    os.system(f"{terminal} sh -c \"cd Security/keys; rm -r *.key'; sleep 1\"")

    os.system(f"pkill -i {terminal}")
    
    sys.exit()


# Compile classes
os.system("mvn clean install")

# Spawn blockchain nodes
with open(f"Service/src/main/resources/{server_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"{terminal} sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']} {server_config}' ; sleep 500\"")
            sys.exit()

with open(f"Client/src/main/resources/{client_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"{terminal} sh -c \"cd Client; mvn exec:java -Dexec.args='{key['id']} {client_config} {server_config}' ; sleep 500\"")
            sys.exit()

signal.signal(signal.SIGINT, quit_handler)

while True:
    print("Type quit to quit")
    command = input(">> ")
    if command.strip() == "quit":
        quit_handler()
