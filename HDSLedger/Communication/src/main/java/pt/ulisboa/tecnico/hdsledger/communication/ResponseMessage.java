package pt.ulisboa.tecnico.hdsledger.communication;

public class ResponseMessage extends Message{

    private String message;

    public ResponseMessage(String senderId, Type type, String message) {
        super(senderId, Type.RESPONSE);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    
}
