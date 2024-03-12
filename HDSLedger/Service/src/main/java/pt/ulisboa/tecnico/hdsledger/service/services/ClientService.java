package pt.ulisboa.tecnico.hdsledger.service.services;


import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());

    private final Link link;

    private final ProcessConfig config;

    private final NodeService service;

    public ClientService(Link linkToClients, ProcessConfig nodeConfig, NodeService nodeService) {
        this.link = linkToClients;
        this.config = nodeConfig;
        this.service = nodeService;
    }

    private void append(AppendMessage message) {
        this.service.startConsensus(message.getMessage(), message.getSenderId());
    }

    public void listen() {
        try {
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();

                        new Thread(() -> {
                            switch (message.getType()) {
                                case APPEND -> {
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received APPEND message from {1}", config.getId(), message.getSenderId()));
                                    append((AppendMessage)message);
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
