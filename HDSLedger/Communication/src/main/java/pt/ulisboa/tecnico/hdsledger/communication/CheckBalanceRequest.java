package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CheckBalanceRequest extends Message{
    
    private String ownerId;

    private int lastReceivedNonce;

    public CheckBalanceRequest(String ownerId, int lastReceivedNonce) {
        super(ownerId, Type.CHECK_BALANCE);
        this.ownerId = ownerId;
        this.lastReceivedNonce = lastReceivedNonce;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public int getLastReceivedNonce() {
        return lastReceivedNonce;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
