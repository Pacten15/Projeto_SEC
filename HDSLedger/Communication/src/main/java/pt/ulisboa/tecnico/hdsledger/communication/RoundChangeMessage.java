package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.Map;

import com.google.gson.Gson;

public class RoundChangeMessage {

    private int preparedRound;
    private String preparedValue;
    private Map<String, ConsensusMessage> preparedMessages; 

    public RoundChangeMessage(int prepareRound, String preparedValue, Map<String, ConsensusMessage> preparedMessages) {
        this.preparedRound = prepareRound;
        this.preparedValue = preparedValue;
        this.preparedMessages = preparedMessages;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public String getPreparedValue() {
        return preparedValue;
    }

    public Map<String, ConsensusMessage> getPreparedMessages() {
        return preparedMessages;
    }
    

    public String toJson() {
        return new Gson().toJson(this);
    }
}