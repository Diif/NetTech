package com.grishaprimilabu.model;

import com.grishaprimilabu.model.field.Field;
import com.grishaprimilabu.proto.SnakesProto;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

public interface Model {

    void applyGameStateMsg(SnakesProto.GameState gameState);

    void applyConfig(SnakesProto.GameConfig config);

    SnakesProto.GameMessage applyJoinMsg(SnakesProto.GameMessage joinMsg, int playerId);

    void applySteerMsg(SnakesProto.GameMessage message, Integer playerId);

    void applyChangeRoleMsg(SnakesProto.GameMessage message);

    void kickPlayer(int id);
    Field getField();

    SnakesProto.GameState getCurState();

    void makeSelfJoin(int playerId);

    void startServerWithNewGame();
    void startServerWithExistedGame(int oldMaster, int newMaster);
    void turnOffServerIfRun();

}
