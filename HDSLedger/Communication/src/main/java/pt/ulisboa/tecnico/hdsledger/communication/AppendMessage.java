package pt.ulisboa.tecnico.hdsledger.communication;

public class AppendMessage extends Message{

    // Message (Append)
    private String message;

    public AppendMessage() {
        super("69", Type.APPEND);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    
}
