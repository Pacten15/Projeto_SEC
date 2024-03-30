package pt.ulisboa.tecnico.hdsledger.communication;

import java.math.BigDecimal;

import com.google.gson.Gson;

public class TransferMessageRequest extends Message {
    
    private String sourceId;
    private String destId;
    private BigDecimal amount;

    public TransferMessageRequest(String sourceId, String destId, BigDecimal amount) {
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

    public BigDecimal getAmount() {
        return amount;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
