package pt.ulisboa.tecnico.hdsledger.service.models;

import java.math.BigDecimal;

public class Account {

    private String ownerId;
    private String publicKey;
    private static final int INITIAL_AMOUNT = 100;
    private BigDecimal balance = new BigDecimal(INITIAL_AMOUNT);
    private int lastSeenNonce;

    public Account(String ownerId, String publicKey) {
        this.ownerId = ownerId;
        this.publicKey = publicKey;
        this.lastSeenNonce = 0;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
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
    
}
