package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CheckBalanceRequest {
    
    private String ownerId;
    private int nonce;

    public CheckBalanceRequest(String ownerId, int nonce) {
        this.ownerId = ownerId;
        this.nonce = nonce;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public int getNonce() {
        return nonce;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
