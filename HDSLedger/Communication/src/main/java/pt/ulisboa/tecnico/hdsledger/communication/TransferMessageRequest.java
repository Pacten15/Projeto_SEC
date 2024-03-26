package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class TransferMessageRequest extends Message {
    
    private String sourceId;
    private String destId;
    private int amount;

    public TransferMessageRequest(String sourceId, String destId, int amount) {
        super(sourceId, Type.TRANSFER);
        this.sourceId = sourceId;
        this.destId = destId;
        this.amount = amount;
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

    public String toJson() {
        return new Gson().toJson(this);
    }
}
