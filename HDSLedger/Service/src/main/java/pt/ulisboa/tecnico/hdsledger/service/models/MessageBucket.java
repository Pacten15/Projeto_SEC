package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());
    // Quorum size
    private final int quorumSize;

    private final int f;
    // Instance -> Round -> Sender ID -> Consensus message
    private final Map<Integer, Map<Integer, Map<String, ConsensusMessage>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
        f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    /*
     * Get messages from the bucket
     * 
     * @param instance
     * 
     * @param round
     */
    public Map<String, ConsensusMessage> getMessages(int instance, int round) {
        bucket.putIfAbsent(instance, new ConcurrentHashMap<>());
        bucket.get(instance).putIfAbsent(round, new ConcurrentHashMap<>());
        return bucket.get(instance).get(round);
    }

    /*
     * Add a message to the bucket
     * 
     * @param consensusInstance
     * 
     * @param message
     */
    public void addMessage(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        this.getMessages(consensusInstance, round).put(message.getSenderId(), message);
    }

    public Optional<String> hasValidPrepareQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        this.getMessages(instance, round).values().forEach((message) -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            String value = prepareMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<String> hasValidCommitQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        this.getMessages(instance, round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            String value = commitMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<Integer> hasValidRoundChangef1(int instance, int round) {
        // check if there are f+1 round change messages
        // all with message round equal above round
        // then return the smallest message round
        int count = 0;
        int lowestRoundChangeRequest = round + 1;

        for (ConsensusMessage message : this.getMessages(instance, round).values()) {
            if (message.getRound() > round) {
                count++;
                if (message.getRound() < lowestRoundChangeRequest) {
                    lowestRoundChangeRequest = message.getRound();
                }
            }
        }

        if (count >= f + 1) {
            return Optional.of(lowestRoundChangeRequest);
        } else {
            return Optional.empty();
        }
    }

    public Optional<String> hasValidRoundChangeQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        this.getMessages(instance, round).values().forEach((message) -> 
        {
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            String value = roundChangeMessage.getPreparedValue();
            System.out.println("roundChangeMessage prepared value: '" + value + "'");
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });
        
        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public List<Object> HighestPrepared(int instance, int round) {
        Map<Integer, String> helperMap = new HashMap<>();

        List<Object> highestPrepareAndRoundChangeMessage = new ArrayList<>();

        RoundChangeMessage[] roundChangeWithHighestPrepare = new RoundChangeMessage[1];

        this.getMessages(instance, round).values().forEach((message) -> 
        {
            int highestPreparedRound = 0;
            String preparedValue = "";
            RoundChangeMessage roundChangeMessage = message.deserializeRoundChangeMessage();
            if(roundChangeMessage.getPreparedRound() > highestPreparedRound) {
                highestPreparedRound = roundChangeMessage.getPreparedRound();
                preparedValue = roundChangeMessage.getPreparedValue();
                roundChangeWithHighestPrepare[0] = roundChangeMessage;
            }
            helperMap.put(highestPreparedRound, preparedValue);
            
        });

        Map.Entry<Integer, String> highPrepareEntry = helperMap.entrySet().stream().max(Map.Entry.comparingByKey()).get();

        highestPrepareAndRoundChangeMessage.add(highPrepareEntry);

        highestPrepareAndRoundChangeMessage.add(roundChangeWithHighestPrepare[0]);

        return highestPrepareAndRoundChangeMessage;
    }


}