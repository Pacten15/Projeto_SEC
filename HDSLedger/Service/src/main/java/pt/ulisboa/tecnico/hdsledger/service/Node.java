package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.security.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.service.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.Behavior;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;


import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    private static String nodesConfigPath = "src/main/resources/";
    private static String clientsConfigPath = "../Client/src/main/resources/regular_config.json";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            nodesConfigPath += args[1];



            // Generate key pair to each server
            CryptoUtils.createKeyPair(4096, "../Security/keys/public_key_server_" + id + ".key" , "../Security/keys/private_key_server_" + id + ".key");
            
            // Create configuration instances
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
            ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();
            ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id)).findAny().get();



            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; behavior: {3}; is leader: {4}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(), nodeConfig.getBehavior(),nodeConfig.isLeader()));

            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), nodeConfigs, ConsensusMessage.class);
            Link linkToClients = new Link(nodeConfig, nodeConfig.getClientPort(), clientConfigs, AppendMessage.class);

            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(linkToNodes, linkToClients, nodeConfig, leaderConfig, nodeConfigs);
            ClientService clientService = new ClientService(linkToClients, nodeConfig, nodeService);

            nodeService.listen();
            clientService.listen();

            if (leaderConfig.getBehavior() == Behavior.NO_CLIENT || leaderConfig.getBehavior() == Behavior.LEADER_PRETENDING){
                LOGGER.log(Level.INFO, "Leader comes up with a value to start consensus");
                nodeService.startConsensus("fake value", "0");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}