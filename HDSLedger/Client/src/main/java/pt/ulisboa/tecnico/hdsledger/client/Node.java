package pt.ulisboa.tecnico.hdsledger.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.ResponseBalance;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessageRequest;
import pt.ulisboa.tecnico.hdsledger.security.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.utilities.Behavior;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());

    private static String nodesConfigPath = "../Service/src/main/resources/regular_config.json";
    private static String clientsConfigPath = "src/main/resources/";

    private static int quorum_f;

    private static int lastReceivedBlock = -1;

    //List of nonces that were used in the transaction between clients in a block
    private static Map<String, Integer> lastSeenNonceEachClient = new HashMap<>();

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
            Link link = new Link(clientConfig, clientConfig.getPort(), nodeConfigs, ClientMessage.class);
            link.randomizeCounter();

            for (ProcessConfig client : clientConfigs){
                lastSeenNonceEachClient.put(client.getId(), 0);
            }

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
                    case "transfer":
                        transfer(input, link, clientConfig, nodeConfigs);
                        break;
                    case "balance":
                        balance(input, link, clientConfig, nodeConfigs);
                        break;
                    default:
                        System.out.println("Unknown command, try 'transfer' or 'balance'.");
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void transfer(String input, Link link, ProcessConfig client, ProcessConfig[] nodeConfigs) {
        String[] parts = input.split(" ");
        if (parts.length < 3) {
            System.out.println("Usage: transfer <receiverId> <amount>");
            return;
        }

        // send to all servers
        String receiverId = parts[1];
        BigDecimal amount = new BigDecimal(parts[2]);
        if(amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Amount must be greater than 0");
            return;
        }

        if(receiverId.equals(client.getId())) {
            System.out.println("Cannot transfer to yourself");
            return;
        }

        TransferMessageRequest transferMessage = new TransferMessageRequest(client.getId(), receiverId, amount);

        if(client.getBehavior() == Behavior.TRANSFER_CLIENT_PRETENDING) {
            transferMessage.setSenderId(receiverId);
            transferMessage.setDestId(client.getId());
            transferMessage.setAmount(new BigDecimal(3000));
        }

        ClientMessage clientMessage = new ClientMessage(client.getId(), Message.Type.TRANSFER, transferMessage.toJson());

        

        if(client.getBehavior() == Behavior.NO_SEND_TO_LEADER) {
            System.out.println("Client is not sending messages to the leader");
            for (ProcessConfig node : nodeConfigs) {
                if (node.isLeader()) {
                    continue;
                }
                link.send(node.getId(), clientMessage);
            }
        } else if (client.getBehavior() == Behavior.DOUBLE_SEND_MESSAGE) {
            System.out.println("Client is sending  double messages");
            link.broadcast(clientMessage);
            link.broadcast(clientMessage);
        } else {
            link.broadcast(clientMessage);
        }

        int received_messages = 0;
        try {
            while (true) {
                Message message = link.receive();

                if (message.getType() == Message.Type.RESPONSE) {
                    if(((ClientMessage) message).getMessage().split(" ").length > 7){
                        int block = Integer.parseInt(((ClientMessage) message).getMessage().split(" ")[3]);
                        //Deal with the nonces that were used in all of the trasanction between clients
                        String[] messageParts = ((ClientMessage) message).getMessage().split(" ");
                        StringBuilder listOfReceivedNoncesStringBuilder = new StringBuilder();
                        for (int i = 6; i < messageParts.length; i++) {
                            listOfReceivedNoncesStringBuilder.append(messageParts[i]).append(" ");
                        }
                        String listOfReceivedNoncesString = listOfReceivedNoncesStringBuilder.toString().trim();
                
                        Map<String, Integer> clientIdNonce = new HashMap<>();
                        String[] pairs = listOfReceivedNoncesString.split(" ");
                        for (String pair : pairs) {
                            String[] keyValue = pair.split(":");
                            clientIdNonce.put(keyValue[0], Integer.parseInt(keyValue[1]));
                        }
                        if(block <= lastReceivedBlock) {
                            continue;
                        } else {
                            lastReceivedBlock = block;
                            //Update the list of nonces that were used in the transaction between clients
                            for (Map.Entry<String, Integer> entry : clientIdNonce.entrySet()) {
                                lastSeenNonceEachClient.put(entry.getKey(), entry.getValue());
                            }                             
                        }
                        //Deal with response messages from transfer messages here
                        if (++received_messages >= quorum_f + 1) {
                            System.out.println(MessageFormat.format("{0} - Received Transfer message from {1} with content {2}", client.getId(), message.getSenderId(), ((ClientMessage) message).getMessage()));
                            break;
                        }
                        
                    }
                    //Deal with response messages from failed transaction messages here
                    if (++received_messages >= quorum_f + 1) {
                        System.out.println(MessageFormat.format("{0} - Received Transfer message from {1} with content {2}", client.getId(), message.getSenderId(), ((ClientMessage) message).getMessage()));
                        break;
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void balance(String input, Link link, ProcessConfig client, ProcessConfig[] nodeConfigs) {
        String[] parts = input.split(" ");
        if (parts.length != 2) {
            System.out.println("Usage: balance <clientId>");
            return;
        }

        // send to all servers
        String clientId = parts[1];
        String publicKeyEncodedString = CryptoUtils.getPublicKeyServerB64EncodedString(clientId);
        System.out.println(" Sending Check with nonce: " + lastSeenNonceEachClient.get(clientId));
        CheckBalanceRequest checkBalanceMessage = new CheckBalanceRequest(clientId, publicKeyEncodedString, lastSeenNonceEachClient.get(clientId));

        ClientMessage clientMessage = new ClientMessage(client.getId(), Message.Type.CHECK_BALANCE, checkBalanceMessage.toJson());

        if(client.getBehavior() == Behavior.NO_SEND_TO_LEADER) {
            System.out.println("Client is not sending messages to the leader");
            for (ProcessConfig node : nodeConfigs) {
                if (node.isLeader()) {
                    continue;
                }
                link.send(node.getId(), clientMessage);
            }
        }
        else {
            link.broadcast(clientMessage);
        }

        // wait for Response message quorum (f + 1 messages) and exit
        // but create thread to wait for all ACKs

        int received_messages = 0;
        try {
            while (true) {
                Message message = link.receive();

                if (message.getType() == Message.Type.RESPONSE_BALANCE) {

                    String messageContent = ((ClientMessage) message).getMessage();

                    String ownerId = messageContent.split(" ")[0];

                    if (++received_messages >= quorum_f + 1 && ownerId.equals(clientId)) {
                        System.out.println(MessageFormat.format("{0} - Received Successful balance message from {1} with content {2}", client.getId(), message.getSenderId(), messageContent));
                        break;
                    }
                    
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
