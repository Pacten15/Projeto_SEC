package pt.ulisboa.tecnico.hdsledger.utilities;


public enum Behavior {
    //Behaves Normal
    NORMAL("NORMAL"),
    //The process ignores all the messages received
    NON_RESPONSIVE("NON_RESPONSIVE"),
    //Sends prePrepare messages as a non leader
    FAKE_PRE_PREPARE("FAKE_PRE_PREPARE"),
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
    //A process does not send prepare messages if is on round 1 upon receiving a pre prepare message
    NO_PREPARE_01("NO_PREPARE_01"),
    //A process does not send commit messages if is on round 1 upon receiving a prepare message
    NO_COMMIT_01("NO_COMMIT_01"),
    //A process that does not verify its messages
    NO_VERIFICATION("NO_VERIFICATION"),
    //A Leader process start a consensus with a predefined value
    NO_CLIENT("NO_CLIENT"),
    //A process thats sleeps for too much time
    SLEEP("SLEEP"),
    //A client process does not send messages to the leader
    NO_SEND_TO_LEADER("NO_SEND_TO_LEADER"),
    //A client process sends a messages pretending to be another client
    TRANSFER_CLIENT_PRETENDING("TRANSFER_CLIENT_PRETENDING"),
    //A client process sends a message
    CHECK_BALANCE_CLIENT_PRETENDING("CHECK_BALANCE_CLIENT_PRETENDING"),
    //A client process sends a message with a wrong nonce
    CHECK_BALANCE_WRONG_NONCE("CHECK_BALANCE_WRONG_NONCE"),
    //A client tries to send the same message twice 
    DOUBLE_SEND_MESSAGE("DOUBLE_SEND_MESSAGE"),
    //A process sends a round change message with a different value
    FAKE_ROUND_CHANGE("FAKE_ROUND_CHANGE"),
     //A server process ignores messages from a client
     IGNORE_CLIENT("IGNORE_CLIENT");
    

    String behavior;

    Behavior(String command) {
        this.behavior = command;
    }
}
