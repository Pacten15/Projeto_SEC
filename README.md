# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.8](https://maven.apache.org/) - Build and dependency management tool;

- [Python 3](https://www.python.org/downloads/) - Programming language;

---

# Configuration Files

### Node configuration

Can be found inside the `resources/` folder of the `Service` module.

```json
{
    "id": <NODE_ID>,
    "isLeader": <IS_LEADER>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
    "clientPort": <CLIENT_PORT>,
    "behaviour": <BEHAVIOUR>
}
```

### Client configuration

Can be found inside the `resources/` folder of the `Client` module.

```json
{
    "id": <CLIENT_ID>,
    "hostname": "localhost",
    "port": <CLIENT_PORT>,
    "behavior": <BEHAVIOR>
}
```

## Dependencies

To install the necessary dependencies run the following command:

```bash
./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their JSON representation.

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes
of the blockchain.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments which can be modified:

- `terminal` - the terminal emulator used by the script
- `server_config` - a string from the array `server_configs` which contains the possible configurations for the blockchain nodes

Run the script with the following command:

```bash
python3 puppet-master.py
```
Note: You may need to install **kitty** in your computer

In Linux:

```bash
sudo apt install kitty
```
sudo apt install kitty

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```
mvn clean install
```

### Execution

Run without arguments

```
cd <module>/
mvn compile exec:java
```

Run with arguments

```
cd <module>/
mvn compile exec:java -Dexec.args="..."
```

### Execution Example

First: 
    Open 4 terminals to host the servers

Second: 
    In each of them go to directory `Service` and run in each terminal these comands being one for each terminal and in this order:

        mvn exec:java '-Dexec.args=2 regular_config.json'

        mvn exec:java '-Dexec.args=3 regular_config.json'

        mvn exec:java '-Dexec.args=4 regular_config.json'

        mvn exec:java '-Dexec.args=1 regular_config.json'

    To Change the behaviour just chose one of the configs presented in the `resources` directory from the `Service` module

Third:
    Open another terminal to host the client

Forth:

    Execute in the terminal the following command:

        mvn exec:java '-Dexec.args=69 regular_config.json'


    Example of the execution the append command in the client side:

        append abcd







---
This codebase was adapted from last year's project solution, which was kindly provided by the following group: [David Belchior](https://github.com/DavidAkaFunky), [Diogo Santos](https://github.com/DiogoSantoss), [Vasco Correia](https://github.com/Vaascoo). We thank all the group members for sharing their code.

