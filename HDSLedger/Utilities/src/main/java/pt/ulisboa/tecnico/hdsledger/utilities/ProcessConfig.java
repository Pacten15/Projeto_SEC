package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {
    public ProcessConfig() {}

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

    private Behaviour behaviour = Behaviour.NORMAL;

    public boolean isLeader() {
        return isLeader;
    }

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public Behaviour getBehaviour() {
        return behaviour;
    }

    public void setBehaviour(Behaviour behaviour) {
        this.behaviour = behaviour;
    }
}