package com.grishaprimilabu.network;

import com.grishaprimilabu.proto.SnakesProto;
import javafx.collections.ObservableList;

public interface NetworkHandler {

    void sendRotation(SnakesProto.Direction direction);

    void sendState(SnakesProto.GameState gameState);
    void fillWithAddress(SnakesProto.GamePlayer.Builder builder, int playerId);

    void sendChangeRoleToViewToMaster();
    void sendChangeRoleToPlayer(SnakesProto.NodeRole role, int playerId);

    void startListeningForGames(ObservableList<String> listForGames);

    void stopListeningForGames();

    void hostNewGame();

    SnakesProto.GameMessage connectToGame(String gameName);
    void askServerForGames(String ip, int port);
    void changeMaster(int deputyId);
    void disconnect();
    void turnOff();

}
