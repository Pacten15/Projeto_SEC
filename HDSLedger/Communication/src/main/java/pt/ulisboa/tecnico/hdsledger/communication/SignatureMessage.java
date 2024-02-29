package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class SignatureMessage {

    private String message;
    
    private String signature;

    public SignatureMessage(String message, String signature) {
        this.message = message;
        this.signature = signature;
    }

    public String getMessage() {
        return message;
    }

    public String getSignature() {
        return signature;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
