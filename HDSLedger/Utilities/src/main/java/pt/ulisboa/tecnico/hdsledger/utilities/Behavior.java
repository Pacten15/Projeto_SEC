package pt.ulisboa.tecnico.hdsledger.utilities;


public enum Behavior {
    //Behaves Normal
    NORMAL("NORMAL"),
    //The process ignores all the messages received
    NON_RESPONSIVE("NON_RESPONSIVE"),
    //Sends prePrepare messages as a non leader
    FAKE_LEADER_PP("FAKE_LEADER_PP"),
    //A non leader process sends a commit and prepare messages as a leader
    FAKE_LEADER_C_P("FAKE_LEADER_C_P"),
    // A Leader sends prePrepare message as a non leader
    LEADER_PRETENDING("LEADER_PRETENDING"),
    //A process sends a COMMIT message with a different value
    FAKE_COMMIT("FAKE_COMMIT"),
    //A process sends a PREPARE message with a different value
    FAKE_PREPARE("FAKE_PREPARE"),
    //A process does not broadcast any message sending only to some other process
    BROADCAST_FAIL("BROADCAST_FAIL"),
    // Every process non leader tries to propose a value different from the leader preprepare value
    COUP_DA_TAT("COUP_DA_TAT"),
    //A process leader tries to commit a value without the IBFT process being finished
    ABSOLUTIST("ABSOLUTIST"),
    //A process that does not verify its messages
    NO_VERIFICATION("NO_VERIFICATION"),
    //A Leader process start a consensus with a predefined value
    NO_CLIENT("NO_CLIENT");
    

    String behavior;

    Behavior(String command) {
        this.behavior = command;
    }
}
