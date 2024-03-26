package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.security.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.utilities.Behavior;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());

    private static String nodesConfigPath = "../Service/src/main/resources/regular_config.json";
    private static String clientsConfigPath = "src/main/resources/";

    private static int quorum_f;

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            clientsConfigPath += args[1];

            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId().equals(id)).findAny().get();

            // count the number of nodes
            quorum_f = Math.floorDiv(nodeConfigs.length - 1, 3);

            CryptoUtils.createKeyPair(4096, "../Security/keys/public_key_server_" + id + ".key" , "../Security/keys/private_key_server_" + id + ".key");

            LOGGER.log(Level.INFO, "Running at " + clientConfig.getHostname() + ":" + clientConfig.getPort() + "; behavior: " + clientConfig.getBehavior());

            for (ProcessConfig node : nodeConfigs) {
                node.setPort(node.getClientPort());
            }
            Link link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, AppendMessage.class);
            link.randomizeCounter();

            Scanner scanner = new Scanner(System.in);

            String input = "";
            System.out.println("Type 'exit' to leave");
            while (true) {

                System.out.print(">>> ");
                input = scanner.nextLine();

                input = input.trim();
                if (input.length() == 0) {
                    continue;
                }

                String[] parts = input.split(" ");
                switch (parts[0]) {
                    case "exit":
                        scanner.close();
                        System.exit(0);
                        break;
                    case "append":
                        append(input, link, clientConfig, nodeConfigs);
                        break;
                    default:
                        System.out.println("Unknown command");
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void append(String input, Link link, ProcessConfig client, ProcessConfig[] nodeConfigs) {
        String[] parts = input.split(" ");
        if (parts.length < 2) {
            System.out.println("Usage: append <message>");
            return;
        }

        // send to all servers
        String messageString = input.substring(parts[0].length() + 1);
        AppendMessage appendMessage = new AppendMessage(client.getId(), messageString);

        if(client.getBehavior() == Behavior.NO_SEND_TO_LEADER) {
            System.out.println("Client is not sending messages to the leader");
            for (ProcessConfig node : nodeConfigs) {
                if (node.isLeader()) {
                    continue;
                }
                link.send(node.getId(), appendMessage);
            }
        }
        else {
            link.broadcast(appendMessage);
        }

        

        // wait for APPEND response quorum (f + 1 messages) and exit
        // but create thread to wait for all ACKs

        int received_messages = 0;
        try {
            while (true) {
                Message message = link.receive();

                if (message.getType() == Message.Type.RESPONSE) {
                    if (++received_messages >= quorum_f + 1) {
                        System.out.println(MessageFormat.format("{0} - Received APPEND SUCCESS message from {1} with content {2}", client.getId(), message.getSenderId(), ((AppendMessage) message).getMessage()));
                        break;
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
