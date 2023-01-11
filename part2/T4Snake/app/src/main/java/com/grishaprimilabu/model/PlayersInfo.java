package com.grishaprimilabu.model;

import com.grishaprimilabu.proto.SnakesProto;
import lombok.Getter;

import java.util.HashMap;


@Getter
public class PlayersInfo {

    public record PlayerWithSnake(SnakesProto.GamePlayer player, SnakesProto.GameState.Snake snake) {}
    private final HashMap<Integer, SnakesProto.GamePlayer> players;
    private final HashMap<Integer, SnakesProto.GameState.Snake> snakes;
    private int numAliveSnakes = 0;

    public PlayersInfo(){
        players = new HashMap<>();
        snakes = new HashMap<>();
    }

    public void clean(){
        numAliveSnakes = 0;
        players.clear();
        snakes.clear();
    }

     public void addPlayerWithSnake(SnakesProto.GamePlayer player, SnakesProto.GameState.Snake snake){
        players.put(player.getId(), player);
        if(!(player.getRole() == SnakesProto.NodeRole.VIEWER)) {
            snakes.put(player.getId(), snake);
            numAliveSnakes++;
        }
    }

     public void putPlayer(SnakesProto.GamePlayer player){
        players.put(player.getId(), player);
    }

     public void putPlayer(Integer id,SnakesProto.GamePlayer player){
        players.put(id, player);
    }
     public void putSnake(SnakesProto.GameState.Snake snake) {snakes.put(snake.getPlayerId(), snake);}

     public void putSnake(Integer id, SnakesProto.GameState.Snake snake) {snakes.put(id, snake);}

     public SnakesProto.GameState.Snake getSnake(Integer id){
        return snakes.get(id);
    }
     public SnakesProto.GamePlayer getPlayer(Integer id){
        return players.get(id);
    }

     public void removePlayerAndMakeSnakeZombie(Integer id){
        if(null != players.remove(id)){
            SnakesProto.GameState.Snake snake = snakes.remove(id);
            if(snake != null){
                numAliveSnakes--;
                snakes.put(id, SnakesProto.GameState.Snake.newBuilder(snake).setState(SnakesProto.GameState.Snake.SnakeState.ZOMBIE).build());
            }
        }
    }


     public void updatePlayersAndSnakes(SnakesProto.GameStateOrBuilder gameState){
        updateSnakes(gameState);

        players.clear();
        for (SnakesProto.GamePlayer gamePlayer : gameState.getPlayers().getPlayersList()){
            players.put(gamePlayer.getId(), gamePlayer);
        }
    }

    public void updateSnakes(SnakesProto.GameStateOrBuilder gameState){
        numAliveSnakes = 0;
        snakes.clear();
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()){
            snakes.put(snake.getPlayerId(), snake);
            if (snake.getState() != SnakesProto.GameState.Snake.SnakeState.ZOMBIE){
                numAliveSnakes++;
            }
        }
    }

}
