package pt.ulisboa.tecnico.hdsledger.communication;

import java.math.BigDecimal;

import com.google.gson.Gson;

public class TransferMessageRequest extends Message {
    
    private String sourceId;
    private String destId;
    private BigDecimal amount;

    private int nonce;

    public TransferMessageRequest(String sourceId, String destId, BigDecimal amount) {
        super(sourceId, Type.TRANSFER);
        this.sourceId = sourceId;
        this.destId = destId;
        this.amount = amount;
        this.nonce = new java.util.Random().nextInt(999999999);
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

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public void setDestId(String destId) {
        this.destId = destId;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }



}
