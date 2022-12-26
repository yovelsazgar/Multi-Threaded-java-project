package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    protected final int NUM_OF_SLOTS = 12;
    private final int MAX_NUM_OF_PLAYERS = 6;

    private int[][] Tokens = new int[NUM_OF_SLOTS][MAX_NUM_OF_PLAYERS];

    public int[][] getTokens(){
        return Tokens;
    }

    protected volatile boolean canPress = false;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;

        for (int i = 0; i < NUM_OF_SLOTS; i++) {
            for (int j = 0; j < MAX_NUM_OF_PLAYERS; j++) {
                Tokens[i][j] = 0;
            }
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     *
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public synchronized void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        // TODO implement
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     *
     * @param slot - the slot from which to remove the card.
     */
    public synchronized void removeCard(int slot, Player[] players) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        // TODO implement
        //remove from other players placed cards
        for (int i = 0; i < players.length; i++) {
            if (players[i].getPlacedCards().contains(slot)) {
                removeToken(players[i].getId(), slot, players);
                Integer slot1 = slot;
                players[i].getPlacedCards().remove(slot1);
            }
        }

        cardToSlot[slotToCard[slot]] = null;
        slotToCard[slot] = null;
        for (int i = 0; i < players.length; i++) {
            removeToken(i, slot, players);
        }
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public synchronized void placeToken(int player, int slot, Player[] players) {
        // TODO implement
        if (Tokens[slot][player] == 0) {
            Tokens[slot][player] = 1;
            env.ui.placeToken(player, slot);
        } else removeToken(player, slot, players);
    }

    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot, Player[] players) {
        // TODO implement
        synchronized (players[player].getPlacedCards()) {
            if (players[player].getPlacedCards().contains(slotToCard[slot]) || Tokens[slot][player] == 1) {
                Tokens[slot][player] = 0;
                env.ui.removeToken(player, slot);
                Integer slot1 = slot;
                players[player].getPlacedCards().remove(slot1);
                return true;
            }
        }
//        if(Tokens[slot][player]==1){
//            Tokens[slot][player]=0;
//            env.ui.removeToken(player,slot);
//            return true;
//        }
        return false;
    }

    public boolean isExistsOnTable(int card) {
        for (int i = 0; i < NUM_OF_SLOTS; i++) {
            if (card == slotToCard[i])
                return true;
        }
        return false;
    }
}
