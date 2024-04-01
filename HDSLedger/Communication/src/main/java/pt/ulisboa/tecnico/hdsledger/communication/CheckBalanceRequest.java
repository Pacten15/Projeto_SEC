package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CheckBalanceRequest extends Message{
    
    private String ownerId;
    private String publicKey;
    private int nonce;

    public CheckBalanceRequest(String ownerId, String publicKey, int nonce) {
        super(ownerId, Type.CHECK_BALANCE);
        this.ownerId = ownerId;
        this.publicKey = publicKey;
        this.nonce = nonce;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
    
}
