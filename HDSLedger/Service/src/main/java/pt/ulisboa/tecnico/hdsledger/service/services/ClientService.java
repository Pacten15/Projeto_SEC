package pt.ulisboa.tecnico.hdsledger.service.services;


import java.io.IOException;
import java.text.MessageFormat;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferMessageRequest;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());

    private final Link link;

    private final ProcessConfig config;

    private final NodeService service;

    private AtomicBoolean clientRequestRunning = new AtomicBoolean(false);
    private AtomicBoolean consensusRunning = new AtomicBoolean(false);
    private AtomicBoolean blockTimerRunning = new AtomicBoolean(false);
    private Timer blockTimer = new Timer();

    private final int maxBlockMessages = 6;
    private Block block = new Block();
    private List<String> clientList = new ArrayList<String>();


    public ClientService(Link linkToClients, ProcessConfig nodeConfig, NodeService nodeService) {
        this.link = linkToClients;
        this.config = nodeConfig;
        this.service = nodeService;
    }

    private void startConsensus() {
        // wait for consensus to finish, then start a new one
        while (!consensusRunning.compareAndSet(false, true)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        blockTimer.cancel();
        blockTimer = new Timer();
        blockTimerRunning.set(false);

        this.service.startConsensus(block, clientList);
        clientList.clear();
        block.clear();

        consensusRunning.set(false);
    }

    private void addTransaction(ClientMessage message) {
        // wait for append to finish, then start a new one
        while (!clientRequestRunning.compareAndSet(false, true)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // if not running, start block timer
        if (blockTimerRunning.compareAndSet(false, true)) {
            blockTimer = new Timer();
            blockTimer.schedule(new TimerTask() {
                public void run() { startConsensus(); }
            }, 1000);
        }

        // add message to block
        block.addMessage(message.toJson());
        clientList.add(message.getSenderId());

        // if block is full, start consensus and clear block
        if (block.size() == maxBlockMessages) {
            
            for (String messages : block.getMessages()) {
                System.out.println("Message in block: " + messages);
            }
            System.out.println("Block is full, starting consensus");
            startConsensus();
            block = new Block();
        }
        clientRequestRunning.set(false);
    }



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
