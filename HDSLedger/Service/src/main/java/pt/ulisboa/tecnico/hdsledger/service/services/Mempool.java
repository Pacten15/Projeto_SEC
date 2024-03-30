package pt.ulisboa.tecnico.hdsledger.service.services;

import java.util.Queue;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Consumer;

import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;

public class Mempool {
    private final Queue<ClientMessage> pool = new LinkedList<>();

    private final int blockSize;

    public Mempool(int blockSize) {
        this.blockSize = blockSize;
    }

    public void remove(ClientMessage message) {
        synchronized (pool) {
            this.pool.remove(message);
        }
    }

    public Optional<Block> add(ClientMessage message) {
        synchronized (pool) {
            this.pool.add(message);
        }
        return this.createBlock();
    }

    public Optional<Block> createBlock() {
        synchronized (pool) {
            if (this.pool.size() < this.blockSize) {
                return Optional.empty();
            }
            Block block = new Block();
            for (int i = 0; i < this.blockSize; i++) {
                block.add(this.pool.poll().toJson());
            }
            return Optional.of(block);
        }
    }
}
