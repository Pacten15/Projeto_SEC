package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RoundChangeMessage {

    private int preparedRound;
    private String preparedValue;

    public RoundChangeMessage(int prepareRound, String preparedValue) {
        this.preparedRound = prepareRound;
        this.preparedValue = preparedValue;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public String getPreparedValue() {
        return preparedValue;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}