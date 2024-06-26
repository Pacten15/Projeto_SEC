package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;

public class InstanceInfo {

    // State Variables
    private int currentRound = 1;
    private int preparedRound = -1;
    private String preparedValue = "";
    private String inputValue;

    private CommitMessage commitMessage;
    private int committedRound = -1;

    private int roundChangeRound = -1;

    public InstanceInfo(String inputValue) {
        this.inputValue = inputValue;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public void setPreparedRound(int preparedRound) {
        this.preparedRound = preparedRound;
    }

    public String getPreparedValue() {
        return preparedValue;
    }

    public void setPreparedValue(String preparedValue) {
        this.preparedValue = preparedValue;
    }

    public String getInputValue() {
        return inputValue;
    }

    public void setInputValue(String inputValue) {
        this.inputValue = inputValue;
    }

    public int getCommittedRound() {
        return committedRound;
    }

    public void setCommittedRound(int committedRound) {
        this.committedRound = committedRound;
    }

    public CommitMessage getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(CommitMessage commitMessage) {
        this.commitMessage = commitMessage;
    }

    public int getRoundChangeRound() {
        return roundChangeRound;
    }

    public void setRoundChangeRound(int roundChangeRound) {
        this.roundChangeRound = roundChangeRound;
    }
}