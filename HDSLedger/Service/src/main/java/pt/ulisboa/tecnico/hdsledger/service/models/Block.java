package pt.ulisboa.tecnico.hdsledger.service.models;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

public class Block {

    private int instance;
    
    private List<String> blockMessages;

    public Block() {
        this.blockMessages = new ArrayList<>();
    }

    public int getInstance() {
        return instance;
    }

    public void setInstance(int instance) {
        this.instance = instance;
    }

    public List<String> getMessages() {
        return blockMessages;
    }

    public void addMessage(String blockMessage) {
        this.blockMessages.add(blockMessage);
    }

    public void clear() {
        this.blockMessages.clear();
    }

    public boolean isEmpty() {
        return this.blockMessages.isEmpty();
    }

    public int size() {
        return this.blockMessages.size();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static Block fromJson(String json) {
        return new Gson().fromJson(json, Block.class);
    }
}
