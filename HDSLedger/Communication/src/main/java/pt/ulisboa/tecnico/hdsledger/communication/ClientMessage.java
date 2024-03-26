package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class ClientMessage extends Message{

    private String message;

    public ClientMessage(String senderId, Type type, String message) {
        super(senderId, type);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    TransferMessageRequest deserializeTransferMessageRequest() {
        return new Gson().fromJson(this.message, TransferMessageRequest.class);
    }

    CheckBalanceRequest deserializeCheckBalanceRequest() {
        return new Gson().fromJson(this.message, CheckBalanceRequest.class);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        return "ClientMessage{" +
                "senderId='" + getSenderId() + '\'' +
                ", messageId=" + getMessageId() +
                ", type=" + getType() +
                ", message='" + message + '\'' +
                ", signature='" + getSignature() + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ClientMessage)) {
            return false;
        }
        ClientMessage messageToCompare = (ClientMessage) obj;
        return messageToCompare.getSenderId().equals(getSenderId()) && 
                    messageToCompare.getMessageId() == getMessageId() && 
                    messageToCompare.getType().equals(getType()) && 
                    messageToCompare.getMessage().equals(message) &&
                    messageToCompare.getSignature().equals(getSignature());
    }
}