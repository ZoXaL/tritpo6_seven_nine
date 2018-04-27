package com.yatty.sevennine.backend.model;

import com.yatty.sevennine.api.Card;
import com.yatty.sevennine.api.PlayerResult;
import com.yatty.sevennine.api.dto.lobby.PrivateLobbyInfo;
import com.yatty.sevennine.api.dto.lobby.PublicLobbyInfo;
import com.yatty.sevennine.api.dto.model.PlayerInfo;
import com.yatty.sevennine.backend.exceptions.logic.FullLobbyException;
import com.yatty.sevennine.backend.exceptions.security.GameAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the stateful logic/game object.
 *
 * @version 26/02/18
 * @author Mike
 */
public class Game {
    public static final Logger logger = LoggerFactory.getLogger(Game.class);
    private static final int MAX_PLAYERS_NUM = 4;

    private String id;
    private String name;
    
    private List<Player> players;
    private int expectedPlayersNum;
    private Player winner;
    
    private Card topCard;
    private int moveNumber;
    
    private Deck deck;

    public Game(String name, int expectedPlayersNum) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.expectedPlayersNum = expectedPlayersNum;
        this.players = new ArrayList<>(MAX_PLAYERS_NUM);
    }

    /**
     * Player is joining the game.
     *
     * @param   user                user that wants to join the game
     */
    public void addPlayer(LoginedUser user) {
        if (players.size() >= expectedPlayersNum) throw new FullLobbyException(this);
        Player player = new Player(user);
        players.add(player);
    }
    
    public void removePlayer(LoginedUser user) {
        checkUserJoined(user);
        for (Player p : players) {
            if (p.getLoginedUser().equals(user)) {
                players.remove(p);
            }
        }
        if (players.size() == 1) {
            winner = players.get(0);
        }
    }
    
    /**
     * Initializes player cards and top card
     */
    public void giveOutCards() {
        if (deck == null) {
            deck = new SevenAteNineDeck(expectedPlayersNum, false);
        }
        topCard = deck.pullStartCard();
        players.forEach(p -> p.setCards(deck.pullCards()));
    }
    
    /**
     * Validates move and updates state if move is right.
     *
     * @param move                  move to
     * @param moveAuthor            author of move
     * @return true                 if move has been accepted
     * @throws GameAccessException  if player was not found in the game
     */
    public boolean acceptMove(@Nonnull Card move, @Nonnull LoginedUser moveAuthor) {
        checkUserJoined(moveAuthor);
        
        logger.trace("Accepting move {} for topCard {}", move, topCard);
        
        if (!topCard.acceptNext(move)) {
            logger.trace("Rejecting move {} for topCard {}", move, topCard);
            return false;
        }

        Player moveWinner = players.stream()
                .filter(p -> moveAuthor.equals(p.getLoginedUser()))
                .findFirst()
                .orElseThrow(() -> new GameAccessException(moveAuthor, this));
        
        setTopCard(move);
        moveNumber++;
        moveWinner.removeCard(move);
        if (moveWinner.getCards().size() <= 1) {
            winner = moveWinner;
        }
        return true;
    }
    
    public void setTopCard(Card topCard) {
        logger.trace("New top card '{}' in the game '{}'", topCard, this.id);
        this.topCard = topCard;
    }
    
    /**
     * @return  unmodifiable view of game players as logined users
     */
    public List<LoginedUser> getLoginedUsers() {
        return Collections.unmodifiableList(
            players.stream()
                .map(Player::getLoginedUser)
                .collect(Collectors.toList()
                )
        );
    }
    
    /**
     * @return  unmodifiable view of game players
     */
    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }
    
    /**
     * Checks if at least one player has legal move.
     *
     * @return  true    if no one has move to do
     */
    public boolean isStalemate() {
        for (Player p : players) {
            if (p.getCards().stream().filter(topCard::acceptNext).count() > 0) {
                return false;
            }
        }
        return true;
    }
    
    public void fixStalemate() {
        // TODO: add logic here
        while (isStalemate()) {
            topCard = Card.getRandomCard();
        }
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public int getExpectedPlayersNum() {
        return expectedPlayersNum;
    }

    public int getMoveNumber() {
        return moveNumber;
    }

    public boolean isFull() {
        return players.size() >= expectedPlayersNum;
    }
    
    public boolean isFinished() {
        return winner != null || players.size() == 0;
    }
    
    @Nullable
    public Player getWinner() {
        return winner;
    }
    
    public Card getTopCard() {
        return topCard;
    }
    
    public void setDeck(Deck deck) {
        this.deck = deck;
    }
    
    public PublicLobbyInfo getPublicLobbyInfo() {
        PublicLobbyInfo publicLobbyInfo = new PublicLobbyInfo();
        publicLobbyInfo.setLobbyId(this.id);
        publicLobbyInfo.setLobbyName(this.name);
        publicLobbyInfo.setMaxPlayersNumber(this.expectedPlayersNum);
        publicLobbyInfo.setCurrentPlayersNumber(this.getPlayers().size());
        return publicLobbyInfo;
    }
    
    public PrivateLobbyInfo getPrivateLobbyInfo() {
        PrivateLobbyInfo privateLobbyInfo = new PrivateLobbyInfo();
        privateLobbyInfo.setPlayers(players
                .stream()
                .map(p -> new PlayerInfo(p.getLoginedUser().getUser().getGeneratedLogin()))
                .collect(Collectors.toList())
        );
        return privateLobbyInfo;
    }
    
    
    /**
     * Checks, that user has already joined this game.
     *
     * @param user                  user to check
     * @throws GameAccessException  if user has not joined the game
     */
    public void checkUserJoined(LoginedUser user) throws GameAccessException {
        for (Player p : players) {
            if (p.getLoginedUser().equals(user)) {
                return;
            }
        }
        throw new GameAccessException(user, this);
    }
    
    /**
     * Represents user, that is playing in game.
     *
     * @author Mike
     * @version 17/02/18
     */
    public class Player {
        private LoginedUser loginedUser;
        private List<Card> cards;
        
        Player(@Nonnull LoginedUser loginedUser) {
            this.loginedUser = loginedUser;
        }
    
        public LoginedUser getLoginedUser() {
            return loginedUser;
        }
    
        public List<Card> getCards() {
            return Collections.unmodifiableList(cards);
        }
    
        void setCards(List<Card> cards) {
            this.cards = cards;
        }
        
        void removeCard(Card card) {
            cards.remove(card);
        }
        
        public PlayerResult getResult() {
            PlayerResult playerResult = new PlayerResult();
            playerResult.setPlayerName(loginedUser.getUser().getGeneratedLogin());
            playerResult.setCardsLeft(cards.size());
            return playerResult;
        }
    
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Player player = (Player) o;
            return Objects.equals(loginedUser, player.loginedUser);
        }
    
        @Override
        public int hashCode() {
            return Objects.hash(loginedUser);
        }
    }
}