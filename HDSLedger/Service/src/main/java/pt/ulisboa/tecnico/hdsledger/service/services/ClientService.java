package pt.ulisboa.tecnico.hdsledger.service.services;


import java.io.IOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.logging.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import pt.ulisboa.tecnico.hdsledger.communication.CheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.utilities.Behavior;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());

    private final Link link;
    private final ProcessConfig config;
    private final NodeService service;
    private final Mempool mempool;

    private List<String> clientList = new ArrayList<String>();


    public ClientService(Link linkToClients, ProcessConfig nodeConfig, NodeService nodeService, Mempool mempool) {
        this.link = linkToClients;
        this.config = nodeConfig;
        this.service = nodeService;
        this.mempool = mempool;
    }

    public synchronized void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setTimer(ClientMessage message) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Timer expired for {1}", config.getId(), message.getSenderId()));
                startConsensus(mempool.forceBlock());
            }
        }, 60000);

        mempool.addTimer(message, timer);
    }

    public void addTransaction(ClientMessage message) {
        // MISSING: check if the message is valid (is author, has enough balance, etc.)
        if(message.getType() != Message.Type.TRANSFER) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction failed for {1}", config.getId(), message.getSenderId()));
            return;
        }

        if (!service.verifyTransactionMessage(message)) {
            link.send(message.getSenderId(), new ClientMessage(config.getId(), Message.Type.RESPONSE, "Invalid transaction"));
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction failed for {1}", config.getId(), message.getSenderId()));
            return;
        }

        if (config.getBehavior() == Behavior.IGNORE_CLIENT && message.getSenderId() == "69") return;

        setTimer(message);
        clientList.add(message.getSenderId());
        startConsensus(this.mempool.add(message));
    }

    public synchronized void checkBalance(ClientMessage message) {

        if (message.getType() != Message.Type.CHECK_BALANCE) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Balance check failed for {1}", config.getId(), message.getSenderId()));
            return;
        }

        CheckBalanceRequest checkBalanceRequest = message.deserializeCheckBalanceRequest();
        String ownerId = checkBalanceRequest.getOwnerId();
        String publicKeyString = checkBalanceRequest.getPublicKey();
        int nonce = checkBalanceRequest.getNonce();
        BigDecimal balance = service.checkBalance(ownerId ,publicKeyString, nonce);
        BigDecimal minus1 = new BigDecimal(-1);
        ClientMessage responseMessage;

        if (balance.compareTo(minus1) == 0) {
            responseMessage = new ClientMessage(config.getId(), Message.Type.RESPONSE_BALANCE,
            "Failed to check balance for " + ownerId);
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Balance check failed for {1}", config.getId(), ownerId));
        } else {
            responseMessage = new ClientMessage(config.getId(), Message.Type.RESPONSE_BALANCE,
            ownerId + " has " + balance + " dollaretas");
        }
        link.send(message.getSenderId(), responseMessage);
        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Balance check message sent with success for {1}", config.getId(), ownerId));
    }

    private void startConsensus(Optional<Block> block) {
        block.ifPresent(b -> {
            for (String messages : b.getMessages()) {
                System.out.println("Message in block: " + messages);
            }
            System.out.println("Block is full, starting consensus");

            service.startConsensus(b, clientList);
            clientList.clear();
        });
    }

    @Override
    public void listen() {
        try {
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();

                        new Thread(() -> {
                            switch (message.getType()) {
                                case TRANSFER -> {
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received Transfer message from {1}", config.getId(), message.getSenderId()));
                                    System.out.println("Message: " + message.toJson());
                                    System.out.println("Message Type: " + message.getType());
                                    addTransaction((ClientMessage) message);
                                }
                                case CHECK_BALANCE -> {
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received Balance message from {1}", config.getId(), message.getSenderId()));
                                    checkBalance((ClientMessage) message);
                                }
                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}", config.getId(), message.getSenderId()));
                                case IGNORE ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received IGNORE message from {1}, ID is {2}", config.getId(), message.getSenderId(), message.getMessageId()));
                                default ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received unknown message from {1}", config.getId(), message.getSenderId()));
                            }
                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
