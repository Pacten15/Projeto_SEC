package pt.ulisboa.tecnico.hdsledger.communication;

public class ResponseBalance extends Message{

    private String message;

    public ResponseBalance(String senderId, String message) {
        super(senderId, Type.RESPONSE_BALANCE);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    
}

