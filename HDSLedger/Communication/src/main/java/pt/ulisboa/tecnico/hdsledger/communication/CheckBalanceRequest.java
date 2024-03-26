package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CheckBalanceRequest extends Message{
    
    private String ownerId;

    public CheckBalanceRequest(String ownerId, int nonce) {
        super(ownerId, Type.CHECK_BALANCE);
        this.ownerId = ownerId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
