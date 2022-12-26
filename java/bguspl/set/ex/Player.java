package bguspl.set.ex;

import bguspl.set.Env;


import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Random;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    private Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private List<Integer> placedCards;
    private ArrayBlockingQueue<Integer> actions;

    private int setOrNot;

    public int getId() {
        return id;
    }

    public List<Integer> getPlacedCards() {
        return placedCards;
    }

    public ArrayBlockingQueue<Integer> getActions() {
        return actions;
    }

    public void setSetOrNot(int val) {
        setOrNot = val;
    }

    public int getSetOrNot() {
        return setOrNot;
    }

    public void releasePlayer() {
        synchronized (this) {
            notifyAll();
        }
    }

    public boolean getTerminate(){
        return terminate;
    }

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        this.placedCards = new LinkedList<>();
        this.actions = new ArrayBlockingQueue<>(3);
        this.dealer = dealer;
        this.setOrNot = -1; // 0 = not a set, 1 = is a set, 2 = card was taken, -1 = nutral
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        int tempSlot = 0;
        try {
            while (!terminate) {
                // TODO implement main player loop
                synchronized (actions) {
                    while (actions.isEmpty()) {
                        try {
                            actions.notifyAll();
                            actions.wait();
                        } catch (InterruptedException ex) {
                            System.out.println(Thread.currentThread().getName() + " i am waiting for the queue not to br empty");
                            break;
                        }
                    }
                    if (terminate && actions.size() == 0)
                        break;
                    tempSlot = actions.poll();
                    if (!human) {
                        actions.notifyAll();
                    }
                }
                if (!table.removeToken(id, tempSlot, dealer.getPlayers())) {
                    if (placedCards.size() < 3) {
                        placedCards.add(tempSlot);
                        table.placeToken(id, tempSlot, dealer.getPlayers());
                        if (placedCards.size() == 3) {
                            synchronized (dealer.getPlayerSet()) {
                                dealer.getPlayerSet().add(this);
                            }
                            synchronized (this) {
                                    dealer.wakeDealer();
                                    while (setOrNot == -1) {
                                        wait();
                                    }
                            }
                            if (setOrNot == 1)
                                point();
                            else if (setOrNot == 0)
                                penalty();
                        }
                        setOrNot = -1;
                    } 
                }
            }
        } catch (InterruptedException ignored) {
            System.out.println("catch player");
        } finally {
            System.out.println("bla bla bla");
        }
        if (!human) try {
            aiThread.interrupt();
            aiThread.join();

        } catch (InterruptedException ignored) {
        }
        env.logger.info("Thread " + Thread.currentThread().

                getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                Random rand = new Random();
                int press = rand.nextInt(env.config.tableSize);
                keyPressed(press);
//                try {
//                    synchronized (this) { wait(); }
//                } catch (InterruptedException ignored) {}
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement

        if (table.canPress && table.slotToCard[slot] != null) {
            synchronized (actions) {
                while (actions.size() == 3) {
                    try {
                        actions.wait();
                    } catch (InterruptedException ex) {
                        System.out.println(Thread.currentThread().getName() + " waiting for the queue to have room");
                        break;
                    }
                }
                if (!terminate) {
                    actions.add(slot);
                }
                actions.notifyAll();
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        score++;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, score);
        freeze(env.config.pointFreezeMillis, System.currentTimeMillis());
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement

        freeze(env.config.penaltyFreezeMillis, System.currentTimeMillis());
        synchronized (actions) {
            actions.clear();
            if (!human)
                actions.notifyAll();
        }
    }

    public void freeze(long time, long startTime) { // freeze
        while (System.currentTimeMillis() - startTime < time) {
            try {
                playerThread.sleep(500);
            } catch (InterruptedException ignored) {

            }
            env.ui.setFreeze(id, time - (System.currentTimeMillis() - startTime));
        }
    }

    public int score() {
        return score;
    }
}
