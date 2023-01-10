package com.grishaprimilabu.controller;

import com.grishaprimilabu.model.Model;
import com.grishaprimilabu.network.NetworkHandler;
import com.grishaprimilabu.proto.SnakesProto;
import javafx.collections.ObservableList;

public class ControllerForViewImpl implements ControllerForView {

    private Model model;

    private NetworkHandler networkHandler;

    public void init(Model model, NetworkHandler networkHandler){
        this.model = model;
        this.networkHandler = networkHandler;
    }

    @Override
    public void moveUp() {
        networkHandler.sendRotation(SnakesProto.Direction.UP);
    }

    @Override
    public void moveDown() {
        networkHandler.sendRotation(SnakesProto.Direction.DOWN);
    }

    @Override
    public void moveLeft() {
        networkHandler.sendRotation(SnakesProto.Direction.LEFT);
    }

    @Override
    public void moveRight() {
        networkHandler.sendRotation(SnakesProto.Direction.RIGHT);
    }

    @Override
    public SnakesProto.GameMessage connectToGame(String gameName) {
        return networkHandler.connectToGame(gameName);
    }

    @Override
    public void askServerForGames(String ip, int port) {
        networkHandler.askServerForGames(ip, port);
    }

    @Override
    public void changeRoleToView(){
        networkHandler.sendChangeRoleToViewToMaster();
    }

    @Override
    public void disconnect(){
            model.turnOffServerIfRun();
            networkHandler.disconnect();
    }
    @Override
    public void turnOffApp() {
        model.turnOffServerIfRun();
        networkHandler.turnOff();
    }

    @Override
    public void startListeningForGames(ObservableList<String> listForGames) {
        networkHandler.startListeningForGames(listForGames);
    }

    @Override
    public void stopListeningForGames() {
        networkHandler.stopListeningForGames();
    }
    @Override
    public void updateConfig(SnakesProto.GameConfig gameConfig) {
        model.applyConfig(gameConfig);
    }
    @Override
    public void startServerModeAndPlay() {
        model.startServerWithNewGame();
        networkHandler.hostNewGame();
    }




}
