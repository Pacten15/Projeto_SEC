package pt.ulisboa.tecnico.hdsledger.communication;

public class AppendMessage extends Message {

    private String message;



    public AppendMessage(String clientId, String message) {
        super(clientId, Type.APPEND);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
