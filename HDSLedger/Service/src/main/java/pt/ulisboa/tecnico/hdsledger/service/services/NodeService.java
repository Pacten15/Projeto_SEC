package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.security.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.Behavior;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;



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

    // Timer used to trigger the round-change algorithm
    private Timer consensusTimer = new Timer();

    // Clients to respond to
    private List<String> currentClients;

    // Ledger (for now, just a list of strings)
    private ArrayList<Block> ledger = new ArrayList<>();

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

    public ArrayList<Block> getLedger() {
        return this.ledger;
    }

    private void stopConsensusTimer() {
        consensusTimer.cancel();
        consensusTimer = new Timer();
    }

    public void setConsensusTimer(ConsensusMessage message) {
        //Set the timer for the non-leader nodes
        System.out.println("Timer has initiated");
        consensusTimer.schedule(new TimerTask() {
            public void run() { uponConsensusTimerExpired(message); }
        }, 5 * 1000 * message.getRound());
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
 
    public ConsensusMessage createConsensusMessage(Block value, int instance, int round) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value.toJson());

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
    public void startConsensus(Block value, List<String> clientList) {

        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance,
                new InstanceInfo(value));      

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

        currentClients = clientList;
        value.setInstance(localConsensusInstance);

        InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
        ConsensusMessage consensusMessage = this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound());

        // Leader broadcasts PRE-PREPARE message
        if (isLeader(this.config.getId())) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
            this.link.broadcast(consensusMessage);
        } else {
            setConsensusTimer(consensusMessage);
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

        if (config.getBehavior() == Behavior.SLEEP) {
            try {
                Thread.sleep(6000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        if (consensusInstance <= lastDecidedConsensusInstance.get()) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received PRE-PREPARE message for Consensus Instance {1}, Round {2} but consensus already decided, ignoring",
                config.getId(), consensusInstance, round));
            return;
        }

        if (consensusInstance > lastDecidedConsensusInstance.get() + 1) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received PRE-PREPARE message for Consensus Instance {1}, Round {2} but process is not ready for it yet, ignoring",
                config.getId(), consensusInstance, round));
            return;
        }

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
            config.getId(), senderId, consensusInstance, round));

        // Verify if pre-prepare was sent by leader or is justified
        if (!isLeader(senderId) && !JustifyPrePrepare(message)) return;

        // check if instance exists
        if (this.instanceInfo.get(consensusInstance) == null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - @@@ PRE-PREPARE message from {1} does not match existing instances @@@", config.getId(), senderId));
            return;
        }


        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                + "replying again to make sure it reaches the initial sender", config.getId(), consensusInstance, round));
        }

        stopConsensusTimer();

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
        if (!isLeader(this.config.getId())&& JustifyPrePrepare(message)) {
            setConsensusTimer(consensusMessage);
        }

        if(config.getBehavior() == Behavior.NO_PREPARE_01 && round == 1) return;
        
        this.link.broadcast(consensusMessage);
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

        if (consensusInstance <= lastDecidedConsensusInstance.get()) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received PREPARE message for Consensus Instance {1}, Round {2} but consensus already decided, ignoring",
                config.getId(), consensusInstance, round));
            return;
        }

        if (consensusInstance > lastDecidedConsensusInstance.get() + 1) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received PREPARE message for Consensus Instance {1}, Round {2} but process is not ready for it yet, ignoring",
                config.getId(), consensusInstance, round));
            return;
        }

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

            if(config.getBehavior() == Behavior.NO_COMMIT_01 && round == 1) return;

            link.send(senderId, m);
            return;
        }

        // Find value with valid quorum
        Optional<Block> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedValue.isPresent() && instance.getPreparedRound() < round) 
        {
            instance.setPreparedValue(preparedValue.get());
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round).values();

            CommitMessage c = new CommitMessage(preparedValue.get().toJson());
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

                if(config.getBehavior() == Behavior.NO_COMMIT_01 && round == 1) return;

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

        if (consensusInstance <= lastDecidedConsensusInstance.get()) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received COMMIT message for Consensus Instance {1}, Round {2} but consensus already decided, ignoring",
            config.getId(), consensusInstance, round));
            return;
        }

        if (consensusInstance > lastDecidedConsensusInstance.get() + 1) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received COMMIT message for Consensus Instance {1}, Round {2} but process is not ready for it yet, ignoring",
            config.getId(), consensusInstance, round));
            return;
        }

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

        Optional<Block> commitValue = commitMessages.hasValidCommitQuorum(config.getId(), consensusInstance, round);

        if (commitValue.isPresent() && instance.getCommittedRound() < round) {
            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            Block value = commitValue.get();

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized(ledger) {
                // Increment size of ledger to accommodate current instance
                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add(new Block());
                }
                
                ledger.add(consensusInstance - 1, value);
                
                // HOW TO PRINT LEDGER NOW?!?!?!
                // LOGGER.log(Level.INFO, MessageFormat.format("{0} - Current Ledger: {1}", config.getId(), String.join("", ledger)));
            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                config.getId(), consensusInstance, round, true));
            
            for (String currentClientId : currentClients) {
                clientLink.send(currentClientId, new AppendMessage(config.getId(), "Success on block " + ledger.size()));
            }

            //reset timer
            stopConsensusTimer();
        }
    }

    public synchronized void uponConsensusTimerExpired(ConsensusMessage message) 
    {
        int consensusInstance = message.getConsensusInstance();
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        int round;

        int preparedRound;
        Block preparedValue;

        round = instance.getCurrentRound() + 1;
        preparedRound = instance.getPreparedRound();
        preparedValue = instance.getPreparedValue();
        instance.setCurrentRound(round);
        
        

        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        Map<String, ConsensusMessage> preparedMessages = prepareMessages.getMessages(consensusInstance, preparedRound);

        RoundChangeMessage roundchangeMessage = new RoundChangeMessage(preparedRound, preparedValue.toJson(), preparedMessages);

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Timer expired, sending ROUND_CHANGE message to all nodes", config.getId()));

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
            .setConsensusInstance(consensusInstance)
            .setRound(round)
            .setMessage(roundchangeMessage.toJson())
            .setReplyTo(senderId)
            .setReplyToMessageId(senderMessageId)     
            .build();

        this.link.broadcast(consensusMessage);
    }

    public synchronized void sendCommitMessagesFromInstanceAlreadyDecided(ConsensusMessage message)
    {
        int consensusInstance = message.getConsensusInstance();
        int commitedRound = instanceInfo.get(consensusInstance).getCommittedRound();

        System.out.println("Sending commit messages from instance already decided number: " + consensusInstance + "and round " + commitedRound);

        Optional<Block> commitValue = commitMessages.hasValidCommitQuorum(config.getId(), consensusInstance, commitedRound);
        if(!commitValue.isPresent()) return;
        Map<String, ConsensusMessage> messagesToSend = commitMessages.getMessages(consensusInstance, commitedRound);
        for(ConsensusMessage m : messagesToSend.values()){
            if (commitValue.isPresent()) {
                CommitMessage c = new CommitMessage(commitValue.get().toJson());
                ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                    .setConsensusInstance(consensusInstance)
                    .setRound(commitedRound)
                    .setReplyTo(m.getSenderId())
                    .setReplyToMessageId(m.getMessageId())
                    .setMessage(c.toJson())
                    .build();
                link.send(message.getSenderId(), consensusMessage);
            }
        }
    }

    public synchronized void uponRoundChange(ConsensusMessage message) 
    {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);
        
        if (instance == null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ROUND_CHANGE message for Consensus Instance {1}, Round {2} but not processed not in current instance, ignoring",
                config.getId(), consensusInstance, round));
            return;
        }

        if(consensusInstance <= lastDecidedConsensusInstance.get())
        {
            System.out.println("Received ROUND_CHANGE message for Consensus Instance " + consensusInstance + ", Round " + round + " but consensus already decided sending commit messages from instance already decided to sender");
            sendCommitMessagesFromInstanceAlreadyDecided(message);
            return;
        }

        if (consensusInstance > lastDecidedConsensusInstance.get() + 1) 
        {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ROUND_CHANGE message for Consensus Instance {1}, Round {2} but process is not ready for it yet, ignoring",
                config.getId(), consensusInstance, round));
            return;
        }

        int currentRound = instance.getCurrentRound();

        Map<String, ConsensusMessage> preparedMessages = message.deserializeRoundChangeMessage().getPreparedMessages();

        roundChangeMessages.addMessage(message);
        if (consensusInstance == lastDecidedConsensusInstance.get()) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ROUND_CHANGE message for Consensus Instance {1}, Round {2} but consensus already decided, ignoring",
                config.getId(), consensusInstance, round));
            return;
        }

        if (instance.getRoundChangeRound() >= currentRound) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Already received ROUND_CHANGE message for Consensus Instance {1}, Round {2}, ignoring, "
                + "replying again to make sure it reaches the initial sender", config.getId(), consensusInstance, currentRound));

            

            RoundChangeMessage roundchangeMessage = new RoundChangeMessage(instance.getRoundChangeRound(), instance.getPreparedValue().toJson(), preparedMessages);
            ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(consensusInstance)
                .setRound(currentRound)
                .setMessage(roundchangeMessage.toJson())    
                .build();

            this.link.broadcast(consensusMessage);

            return;
        }

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ROUND_CHANGE message from {1}: Consensus Instance {2}, Round {3}",
            config.getId(), message.getSenderId(), consensusInstance, round));

        Optional<Integer> r_min = roundChangeMessages.hasValidRoundChangef1(consensusInstance, round);
        Optional<Block> pV = roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round);

        if (!r_min.isPresent() && !pV.isPresent()) return;
        if (r_min.isPresent()) instance.setCurrentRound(r_min.get());

        // first, check if reached a quorum of round change messages
        if (pV.isPresent() && instance.getRoundChangeRound() < currentRound) {
            instance.setRoundChangeRound(currentRound);

            LOGGER.log(Level.INFO, MessageFormat.format(
                    "####################################\n"+ 
                    "{0} - Reached a Quorum of ROUND_CHANGE\n" +
                    "####################################",
            config.getId()));
            makeLeader(nextLeader());
            LOGGER.log(Level.INFO, leaderConfig.getId() + " is the new leader");

            if (isLeader(config.getId()) && JustifyRoundChange(consensusInstance, instance.getCurrentRound())) {

                LOGGER.log(Level.INFO, MessageFormat.format(
                    "###################\n"+ 
                    "{0} - I AM THE LEADER\n" +
                    "###################",
            config.getId()));

                
                // Leader broadcasts PRE-PREPARE message
                Block value = ((Map.Entry<Integer, Block>) roundChangeMessages.HighestPrepared(consensusInstance, round).get(0)).getValue();
                if (value.isEmpty()) {
                    value = instance.getInputValue();
                }

                this.link.broadcast(this.createConsensusMessage(value, consensusInstance, instance.getCurrentRound()));
            }

            return;
        }

        RoundChangeMessage roundchangeMessage = new RoundChangeMessage(instance.getPreparedRound(), instance.getPreparedValue().toJson(), preparedMessages);
        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
            .setConsensusInstance(consensusInstance)
            .setRound(currentRound)
            .setMessage(roundchangeMessage.toJson())    
            .build();

        this.link.broadcast(consensusMessage);
    }

    public boolean verifySignaturePreparedMessages(Map<String, ConsensusMessage> prepareMessages) {
        for (ConsensusMessage consensusMessage : prepareMessages.values()) {
            String messageSignature = consensusMessage.getSignature();
            consensusMessage.setSignature("");
            String messageContent = new Gson().toJson(consensusMessage);
            consensusMessage.setSignature(messageSignature);
            PublicKey publicKey = CryptoUtils.getPublicKey("../Security/keys/public_key_server_" + consensusMessage.getSenderId() + ".key");
            if(messageSignature != null) {
                if(!CryptoUtils.verifySignature(messageContent, messageSignature, publicKey) && !config.getId().equals(consensusMessage.getSenderId())) {
                    LOGGER.log(Level.INFO, MessageFormat.format("#################################"+
                    "{0} - Invalid signature from {1}", config.getId(), consensusMessage.getSenderId())+
                    "#################################");
                    return false;
                } else {
                    LOGGER.log(Level.INFO, MessageFormat.format("#################################"+
                    "{0} - Valid signature from {1}", config.getId(), consensusMessage.getSenderId())
                    + "#################################");
                }
            }
        }
        return true;
    }

    public boolean JustifyRoundChange(int instance, int round)
    {
        int previous_round = round - 1;
        Optional<Block> pV = roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), instance, round);

        // no roundchange quorum, no justify
        if (!pV.isPresent()) return false;
        // no preparedValue, justify
        if (pV.get().isEmpty()) return true;
        // no prepare quorum, no justify
        Optional<Block> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), instance, previous_round);
        if (!preparedValue.isPresent()) return false;

        // highestPrepared != preparedValue, no justify
        List<Object> highestPrepared = roundChangeMessages.HighestPrepared(instance, round);
        Map.Entry<Integer,Block> entry = (Map.Entry<Integer,Block>) highestPrepared.get(0);
        RoundChangeMessage roundChangeMessage = (RoundChangeMessage) highestPrepared.get(1);
        if (entry.getValue().equals(preparedValue.get())) {
            if(!verifySignaturePreparedMessages(roundChangeMessage.getPreparedMessages())) return false;
            return true;
        }
        return false;
    }

public boolean JustifyPrePrepare(ConsensusMessage message) 
    { 
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        if (round == 1) return true;

        // no roundchange quorum, no justify
        Optional<Block> pV = roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round);
        if (!pV.isPresent()) return false;

        // no preparedValue, justify
        if (pV.get().equals("")) return true;

        // no prepare quorum, no justify
        Optional<Block> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round - 1);
        if (!preparedValue.isPresent()) return false;

        // highestPrepared != preparedValue, no justify
        List<Object> highestPrepared = roundChangeMessages.HighestPrepared(consensusInstance, round);
        Map.Entry<Integer, Block> entry = (Map.Entry<Integer, Block>) highestPrepared.get(0);
        RoundChangeMessage roundChangeMessage = (RoundChangeMessage) highestPrepared.get(1);
        if (entry.getValue().equals(preparedValue.get())) {
            if(!verifySignaturePreparedMessages(roundChangeMessage.getPreparedMessages())) return false;
            return true;
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
    public void sendFakePrePrepareMessage(Block value) {
        if(!isLeader(config.getId()) && config.getBehavior() == Behavior.FAKE_PRE_PREPARE){
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Fake pre prepare message", config.getId()));
            int localConsensusInstance = this.consensusInstance.incrementAndGet();
            this.instanceInfo.put(localConsensusInstance, new InstanceInfo(value));      

            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            this.link.broadcast(this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound()));
        }
    }


    public void sendAckofIgnore() {
        this.link.send(config.getId(), new Message(config.getId(), Message.Type.ACK));
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
                                case IGNORE -> {
                                    sendAckofIgnore();
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received IGNORE message from {1}", config.getId(), message.getSenderId()));
                                }
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