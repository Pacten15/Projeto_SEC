package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class TransferMessageRequest {
    
    private String sourceId;
    private String destId;
    private int amount;
    private int nonce;

    public TransferMessageRequest(String sourceId, String destId, int amount, int nonce) {
        this.sourceId = sourceId;
        this.destId = destId;
        this.amount = amount;
        this.nonce = nonce;
    }


    public String getSourceId() {
        return sourceId;
    }

    public String getDestId() {
        return destId;
    }

    public int getAmount() {
        return amount;
    }

    public int getNonce() {
        return nonce;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
