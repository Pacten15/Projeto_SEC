package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CommitMessage {

    // Value
    private String value;

    public CommitMessage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
