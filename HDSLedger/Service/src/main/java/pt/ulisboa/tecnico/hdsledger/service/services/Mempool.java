package pt.ulisboa.tecnico.hdsledger.service.services;

import java.util.Queue;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Timer;
import java.util.HashMap;
import java.util.Map;

import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.communication.ClientMessage;

public class Mempool {
    private final Queue<ClientMessage> pool = new LinkedList<>();
    private final Map<ClientMessage, Timer> timers = new HashMap<>();

    private final int blockSize;

    public Mempool(int blockSize) {
        this.blockSize = blockSize;
    }

    public void remove(ClientMessage message) {
        synchronized (pool) {
            this.pool.remove(message);
            this.removeTimer(message);
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
                ClientMessage message = this.pool.poll();
                block.add(message.toJson());
                this.removeTimer(message);
            }
            return Optional.of(block);
        }
    }

    public Optional<Block> forceBlock() {
        synchronized (pool) {
            Block block = new Block();
            while (!this.pool.isEmpty() && block.size() < this.blockSize) {
                ClientMessage message = this.pool.poll();
                block.add(message.toJson());
                this.removeTimer(message);
            }
            return Optional.of(block);
        }
    }

    public void addTimer(ClientMessage message, Timer timer) {
        synchronized (pool) {
            this.timers.put(message, timer);
        }
    }

    public void removeTimer(ClientMessage message) {
        Timer timer = this.timers.remove(message);
        if (timer != null) {
            timer.cancel();
        }
    }
}
