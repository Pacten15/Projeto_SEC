package pt.ulisboa.tecnico.hdsledger.service.models;

import java.math.BigDecimal;

public class Account {

    private String ownerId;
    private String publicKeyEncodedString;
    private static final int INITIAL_AMOUNT = 10000;
    private BigDecimal balance = new BigDecimal(INITIAL_AMOUNT);
    private int lastSeenNonce;

    public Account(String ownerId, String publicKeyEncodeString) {
        this.ownerId = ownerId;
        this.publicKeyEncodedString = publicKeyEncodeString;
        this.lastSeenNonce = 0;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getpublicKeyEncodedString() {
        return publicKeyEncodedString;
    }

    public void setpublicKeyEncodedString(String publicKey) {
        this.publicKeyEncodedString = publicKey;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = new BigDecimal(balance);
    }

    public int getLastSeenNonce() {
        return lastSeenNonce;
    }

    public void setLastSeenNonce(int lastSeenNonce) {
        this.lastSeenNonce = lastSeenNonce;
    }

    public void decreaseBalance(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void increaseBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
    
}
