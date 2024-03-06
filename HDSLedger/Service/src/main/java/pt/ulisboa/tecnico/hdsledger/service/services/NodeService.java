package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.Behavior;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

import java.util.Timer;
import java.util.TimerTask;



public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    // Nodes configurations
    private final ProcessConfig[] nodesConfig;

    // Current node is leader
    private final ProcessConfig config;
    // Leader configuration
    private ProcessConfig leaderConfig;

    // Link to communicate with nodes
    private final Link link;

    private final Link clientLink;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;
    // Consensus instance -> Round -> List of round change messages
    private final MessageBucket roundChangeMessages;

    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);

    //Timer used by the non-leader nodes to send round change messages in case it expires 
    private Timer timer = new Timer();



    // Ledger (for now, just a list of strings)
    private ArrayList<String> ledger = new ArrayList<String>();

    public NodeService(Link link, Link clientLink, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig) {

        this.link = link;
        this.clientLink = clientLink;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
        this.roundChangeMessages = new MessageBucket(nodesConfig.length);
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public ArrayList<String> getLedger() {
        return this.ledger;
    }

    private void stopTimer() {
        timer.cancel();
        timer = new Timer();
    }

    private boolean isLeader(String id) {
        return this.leaderConfig.getId().equals(id);
    }

    private String nextLeader() {
        // make list of node IDs, sort them, pick the current and print the next in line
        String[] nodeIds = Arrays.stream(nodesConfig).map(ProcessConfig::getId).toArray(String[]::new);
        Arrays.sort(nodeIds);
        int index = Arrays.binarySearch(nodeIds, leaderConfig.getId());
        String next = nodeIds[(index + 1) % nodeIds.length];
        return next;
    }

    private void makeLeader(String id) {
        this.leaderConfig = Arrays.stream(nodesConfig).filter(c -> c.getId().equals(id)).findAny().get();
    }
 
    public ConsensusMessage createConsensusMessage(String value, int instance, int round) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
            .setConsensusInstance(instance)
            .setRound(round)
            .setMessage(prePrepareMessage.toJson())
            .build();
        
        /*Mostrar ao prof */
        sendMessageAsAnotherServer(consensusMessage, "2");

        return consensusMessage;
    }

    /*
     * Start an instance of consensus for a value
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
    public void startConsensus(String value, String clientId) {
        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(value, clientId));      

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}", config.getId(), localConsensusInstance));
            return;
        }

        // Only start a consensus instance if the last one was decided
        // We need to be sure that the previous value has been decided
        while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Leader broadcasts PRE-PREPARE message
        if (isLeader(this.config.getId())) {
            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
            this.link.broadcast(this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound()));
        } else {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     *
     * @param message Message to be handled
     */
    public void uponPrePrepare(ConsensusMessage message) 
    {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();
        // String value = prePrepareMessage.getValue();

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
            config.getId(), senderId, consensusInstance, round));

        // Verify if pre-prepare was sent by leader or is justified
        // if (!isLeader(senderId) && !JustifyPrePrepare(message)) return;

        if (!isLeader(senderId)) return;

        // Set instance value
        // this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));

        // check if instance exists
        if (this.instanceInfo.get(consensusInstance) == null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - @@@ PRE-PREPARE message from {1} does not match existing instances @@@", config.getId(), senderId));
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                + "replying again to make sure it reaches the initial sender", config.getId(), consensusInstance, round));
        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

        makeFakePrepare(prepareMessage);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
            .setConsensusInstance(consensusInstance)
            .setRound(round)
            .setMessage(prepareMessage.toJson())
            .setReplyTo(senderId)
            .setReplyToMessageId(senderMessageId)
            .build();

        //Initializes the timer for the non-leader nodes
        if (!isLeader(this.config.getId())) {
            setTimer(consensusMessage);
        }

        if (round != 1) {
            // Leader broadcasts PREPARE message
            this.link.broadcast(consensusMessage);
        }
    }

    /*
    * Initiates a timer and executes the round change message if it expires
    *
    * @param message Message to be handled
    */
    public void setTimer(ConsensusMessage message) {
        //Set the timer for the non-leader nodes
        System.out.println("Timer has initiated");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                uponTimerExpired(message);
            }
        }, 1000);
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrepare(ConsensusMessage message) 
    {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
            config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance values
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        // Late prepare (consensus already ended for other nodes) only reply to him (as
        // an ACK)
        if (instance.getPreparedRound() >= round) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
                + "replying again to make sure it reaches the initial sender", config.getId(), consensusInstance, round));

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setReplyTo(senderId)
                .setReplyToMessageId(message.getMessageId())
                .setMessage(instance.getCommitMessage().toJson())
                .build();
                
            makeMeLeaderCP(m);

            link.send(senderId, m);
            return;
        }

        // Find value with valid quorum
        Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedValue.isPresent() && instance.getPreparedRound() < round) 
        {
            instance.setPreparedValue(preparedValue.get());
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round).values();

            CommitMessage c = new CommitMessage(preparedValue.get());
            instance.setCommitMessage(c);

            makeFakeCommit(c);

            sendersMessage.forEach(senderMessage -> 
            {
                ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setReplyTo(senderMessage.getSenderId())
                        .setReplyToMessageId(senderMessage.getMessageId())
                        .setMessage(c.toJson())
                        .build();

                makeMeLeaderCP(m);

                link.send(senderMessage.getSenderId(), m);
            });
        }
    }

    /*
     * Handle commit messages and decide if there is a valid quorum
     *
     * @param message Message to be handled
     */
    public synchronized void uponCommit(ConsensusMessage message) 
    {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
            config.getId(), message.getSenderId(), consensusInstance, round));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) 
        {
            // Should never happen because only receives commit as a response to a prepare message
            MessageFormat.format("{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                config.getId(), message.getSenderId(), consensusInstance, round);
            return;
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                config.getId(), consensusInstance, round));
            return;
        }

        Optional<String> commitValue = commitMessages.hasValidCommitQuorum(config.getId(), consensusInstance, round);

        if (commitValue.isPresent() && instance.getCommittedRound() < round) {
            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            String value = commitValue.get();

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized(ledger) {
                // Increment size of ledger to accommodate current instance
                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add("");
                }
                
                ledger.add(consensusInstance - 1, value);
                
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Current Ledger: {1}", config.getId(), String.join("", ledger)));
            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                config.getId(), consensusInstance, round, true));
            
            if (isLeader(config.getId()) && instance.getClientId() != "" && clientLink != null) {
                clientLink.send(instance.getClientId(), new AppendMessage(config.getId(), "Success on block " + ledger.size()));
            }

            //reset timer
            stopTimer();
        }
    }

    public synchronized void uponTimerExpired(ConsensusMessage message) 
    {
        int consensusInstance = message.getConsensusInstance();
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        int round = instance.getCurrentRound();
        String preparedValue = instance.getInputValue();

        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        RoundChangeMessage roundchangeMessage = new RoundChangeMessage(round, preparedValue);

        System.out.println("Timer expired, sending round change message to all nodes");

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
            .setConsensusInstance(consensusInstance)
            .setRound(round + 1)
            .setMessage(roundchangeMessage.toJson())
            .setReplyTo(senderId)
            .setReplyToMessageId(senderMessageId)     
            .build();

        this.link.broadcast(consensusMessage);
    }

    public synchronized void uponRoundChange(ConsensusMessage message) 
    {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);
        String preparedValue = instance.getInputValue();
        int currentRound = instance.getCurrentRound();
        int newRound = currentRound + 1;

        roundChangeMessages.addMessage(message);

        if (instance.getRoundChangeRound() >= currentRound) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Already received ROUND_CHANGE message for Consensus Instance {1}, Round {2}, ignoring, "
                + "replying again to make sure it reaches the initial sender", config.getId(), consensusInstance, currentRound));

            RoundChangeMessage roundchangeMessage = new RoundChangeMessage(newRound, preparedValue);
            ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(consensusInstance)
                .setRound(newRound)
                .setMessage(roundchangeMessage.toJson())    
                .build();

            this.link.broadcast(consensusMessage);

            return;
        }

        // Third Upon logic
        // Find value with valid quorum
        Optional<String> pV = roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round);
        if (pV.isPresent() && instance.getRoundChangeRound() < currentRound)
        {
            instance.setRoundChangeRound(currentRound);
            //If is not leader and RoundChange is not justified return
            // if (!isLeader(senderId) && !JustifyRoundChange(consensusInstance, newRound)) return;

            System.out.println("!!! Reached a quorum of round change messages !!!");

            instance.setCurrentRound(newRound);
            makeLeader(nextLeader());

            if (isLeader(config.getId())) 
            {
                // Leader broadcasts PRE-PREPARE message
                String value = instance.getInputValue();
                this.link.broadcast(this.createConsensusMessage(value, consensusInstance, newRound));
            }
        }
    }

    public boolean JustifyRoundChange(int instance, int round) 
    {
        // A correct process considers a quorum of round-change message to be justified if one of the conditions (J1, J2) is true
        if(this.roundChangeMessages.JustifyRoundChangeJ1(instance, round)) return true;
        if(JustifyRoundChangeJ2(instance, round)) return true;

        return false;
    } 

    public boolean JustifyRoundChangeJ2(int instance, int round) 
    {
        InstanceInfo instanceInfo = this.instanceInfo.get(instance);
        // A justificação tem uma quorum prepare message válida tal que  
        // a round-change message é a message com a highest prepared round diferente do vazio no quorum
        Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), instance, round);
        if (preparedValue.isPresent() && instanceInfo.getRoundChangeRound() < round) 
        {
            String value = roundChangeMessages.HighestPrepared(instance, round).getValue();
            if (preparedValue.toString() == value) return true;
        }
        return false;
    }

    public boolean JustifyPrePrepare(ConsensusMessage message) 
    {  
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        if (round == 1) return true;

        if (round > 1)  
        {
            InstanceInfo instance = this.instanceInfo.get(consensusInstance);
            Optional<String> pV = roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round);
            if (pV.isPresent() && instance.getRoundChangeRound() < round) 
            {
                if(this.roundChangeMessages.JustifyRoundChangeJ1(consensusInstance, round) || JustifyRoundChangeJ2(consensusInstance, round)) 
                {
                    if(JustifyRoundChangeJ2(consensusInstance, round)) 
                    {
                        ///TODO: I commented this because in the J2 we have this condition, but we can confirm this with DEBUG!!!
                        //String value = roundChangeMessages.HighestPrepared(consensusInstance, round).getValue();
                        instance.setInputValue(pV.toString());
                        return true;
                    }
                    return true;
                }
            }
        }
        return false;  
    }

    /*
     * Send a message pretending to be the leader
     *
     * @param message ConsensusMessage that we want to pretend to be the leader
     */
    public void makeMeLeaderCP(ConsensusMessage message) {
        if(!isLeader(config.getId()) && config.getBehavior() == Behavior.FAKE_LEADER_C_P){
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Making me leader", config.getId()));
            message.setSenderId(this.leaderConfig.getId());
        }
    }

    /*
     * Send a message pretending to be the leader
     *
     * @param message ConsensusMessage that we want to pretend to be the leader and the id of the node that we want to make leader
     */
    public void sendMessageAsAnotherServer(ConsensusMessage message, String id) {
        if( isLeader(config.getId()) && config.getBehavior() == Behavior.LEADER_PRETENDING){
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Making leader the non leader", id));
            message.setSenderId(id);
            PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();
            prePrepareMessage.setValue("accepted a value given by a non leader gotcha");
            message.setMessage(prePrepareMessage.toJson());
        }
    }

    /*
     *  Make fake commit messages to send to the other nodes
     * 
     * @param message ConsensusMessage that we want to change 
     */
     public void makeFakeCommit(CommitMessage message) {
        if(config.getBehavior() == Behavior.FAKE_COMMIT){
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Making fake commit", config.getId()));
            message.setValue("fake commit");
        }
    }

    /*
     *  Make fake prepare messages to send to the other nodes
     * 
     * @param message ConsensusMessage that we want to change 
     */
     public void makeFakePrepare(PrepareMessage message) {
        if(config.getBehavior() == Behavior.FAKE_PREPARE){
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Making fake prepare", config.getId()));
            message.setValue("fake prepare");
            
        }
    }

    /*
     *  Make fake pre prepare messages to send to the other nodes
     * 
     * @param message ConsensusMessage that we want to change 
     */
    public void sendFakePrePrepareMessage(String value) {
        if(!isLeader(config.getId()) && config.getBehavior() == Behavior.FAKE_PRE_PREPARE){
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Fake pre prepare message", config.getId()));
            int localConsensusInstance = this.consensusInstance.incrementAndGet();
            this.instanceInfo.put(localConsensusInstance, new InstanceInfo(value, "0"));      

            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            this.link.broadcast(this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound()));
        }
    }

    @Override
    public void listen() 
    {
        try {
            // Thread to listen on every request
            new Thread(() -> 
            {
                try {
                    while (true) 
                    {
                        Message message = link.receive();

                        if(config.getBehavior() == Behavior.NON_RESPONSIVE) 
                        {
                            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Ignoring message from {1}", config.getId(), message.getSenderId()));
                            continue;
                        }
                   
                        // Separate thread to handle each message
                        new Thread(() -> 
                        {
                            switch (message.getType()) 
                            {
                                case PRE_PREPARE ->
                                    uponPrePrepare((ConsensusMessage) message);
                                case PREPARE ->
                                    uponPrepare((ConsensusMessage) message);
                                case COMMIT ->
                                    uponCommit((ConsensusMessage) message);                            
                                case ROUND_CHANGE ->
                                    uponRoundChange((ConsensusMessage) message);
                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}", config.getId(), message.getSenderId()));
                                case IGNORE ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received IGNORE message from {1}", config.getId(), message.getSenderId()));
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