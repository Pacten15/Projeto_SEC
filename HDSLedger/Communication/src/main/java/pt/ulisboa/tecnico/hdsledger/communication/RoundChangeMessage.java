package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RoundChangeMessage {

    // Value
    private String value;

    public RoundChangeMessage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}