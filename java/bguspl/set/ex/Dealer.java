package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    protected int playersAmount;

    private Thread[] playerThread;

    private int SET_SIZE = 3;

    private ArrayBlockingQueue<Player> playerSet;

    private Object lock;


    public Player[] getPlayers() {
        return players;
    }

    public ArrayBlockingQueue<Player> getPlayerSet() {
        return playerSet;
    }

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        this.playersAmount = players.length;
        this.playerThread = new Thread[playersAmount];
        this.playerSet = new ArrayBlockingQueue<Player>(players.length);
        this.lock = new Object();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        placeCardsOnTable();
        for (int i = playersAmount -1; i >= 0; i--) {
            playerThread[i] = new Thread(players[i]);
            playerThread[i].start();
        }
        while (!shouldFinish()) {
            timerLoop();
            updateTimerDisplay(true);
            synchronized (table) {
                removeAllCardsFromTable();
                if (terminate == false)
                    placeCardsOnTable();
            }
        }
        terminate();
        removeAllCardsFromTable();
        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 999;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            updateTimerDisplay(false);
            sleepUntilWokenOrTimeout();
            Player playerId;
            synchronized (playerSet) {
                if (playerSet.size() != 0) {
                    playerId = playerSet.remove();
                    ifSet(playerId.getId());
                }
            }
        }
    }

    public void ifSet(int id) {
        synchronized (players[id]) {
        int[] cards = new int[SET_SIZE];
        if (players[id].getPlacedCards().size() != 3)
            players[id].setSetOrNot(2);
        if (players[id].getSetOrNot() != 2) {
            int y = 0;
            while (y < players[id].getPlacedCards().size()) {
                cards[y] = table.slotToCard[players[id].getPlacedCards().get(y)];
                y++;
            }
            if (env.util.testSet(cards)) { //it is a set
                players[id].setSetOrNot(1);
                int[] slots = new int[SET_SIZE];
                for (int i = 0; i < 3; i++)
                    slots[i] = players[id].getPlacedCards().get(i);
                removeCardsFromTable(slots);
                if (!terminate)
                    placeCardsOnTable();
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 999;
            } else { //not set
                players[id].setSetOrNot(0);
            }
        }
        players[id].releasePlayer();
        }
    }

    public boolean partOfSet(Player temp, int[] cards) {
        for (int j = 0; j < SET_SIZE; j++) {
            if (temp.getPlacedCards().contains(cards[j]))
                return true;
        }
        return false;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        for (int i = 0; i < players.length; i++) {
                players[i].terminate();
                players[i].setSetOrNot(2);
                playerThread[i].interrupt();
               try {
                   playerThread[i].join();
               } catch (InterruptedException e) {
               }

        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(int[] slots) {
        // TODO implement
        synchronized (table) {
            for (int i = 0; i < SET_SIZE; i++) {
                table.removeCard(slots[i], players);
            }
        }
        if (shouldFinish())
            terminate = true;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    protected void placeCardsOnTable() {
        // TODO implement
        Random rand = new Random();
        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.slotToCard[i] == null) {
                if (deck.size() >= 1) {
                    int deckOut = rand.nextInt(deck.size());
                    int putCard = deck.get(deckOut);
                    table.placeCard(putCard, i);
                    deck.remove(deckOut);
                }
            }
        }
        table.canPress = true;

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        synchronized (lock) {
            try {
                lock.wait(10);
            } catch (InterruptedException e) {
                System.out.println(" dealer was interrupted from sleepUntil");
            }
        }
    }

    public void wakeDealer() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset == true) {
            env.ui.setCountdown(env.config.turnTimeoutMillis + 999, false);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 999;
        } else if (reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis) {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
        } else {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.slotToCard[i] != null) {
                int temp = table.slotToCard[i];
                if (!terminate)
                    deck.add(temp);
                table.removeCard(i, players);
            }
            for (int j = 0; j < playersAmount; j++) {
                table.removeToken(players[j].getId(), i, players);
                //need to empty placed cards and actions
            }
        }
        //need to empty players set
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int max = 0;
        int count = 0;
        for (int i = 0; i < playersAmount; i++) {
            if (players[i].score() > max) {
                max = players[i].score();
                count = 0;
            }
            if (players[i].score() == max)
                count++;
        }
        int[] winners = new int[count];
        int j = 0;
        for (int i = 0; i < playersAmount; i++) {
            if (players[i].score() == max) {
                winners[j] = players[i].getId();
                j++;
            }
        }
        env.ui.announceWinner(winners);
    }
}
