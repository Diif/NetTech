package com.grishaprimilabu.model;

import com.grishaprimilabu.model.entities.Config;
import com.grishaprimilabu.model.field.Field;
import com.grishaprimilabu.model.field.FieldTitle;
import com.grishaprimilabu.model.field.GameField;
import com.grishaprimilabu.network.NetworkHandler;
import com.grishaprimilabu.proto.SnakesProto;
import com.grishaprimilabu.view.View;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class GameModel implements Model {

    private GameField gameField;
    private final PlayersInfo playersInfo;
    private Config config;
    private ServerLogic serverLogic = null;
    private final View view;
    private final NetworkHandler networkHandler;

    @Getter
    private SnakesProto.GameState curState;
    private final Object deputyMonitor = new Object();
    private Integer deputyId;

    public GameModel(View view, NetworkHandler networkHandler, Config config){
        this.networkHandler = networkHandler;
        this.view = view;
        this.config = config;
        playersInfo = new PlayersInfo();
        this.gameField = new GameField(config.getWidth(), config.getHeight());
    }

    @Override
    public void applyGameStateMsg(SnakesProto.GameState gameState){
        if(serverLogic == null){
            updateGameState(gameState);
        }
    }

    @Override
    public void applyChangeRoleMsg(SnakesProto.GameMessage message){
        if(serverLogic != null){
            serverLogic.applyChangeRoleMsg(message);
        } else{
            if(message.getRoleChange().hasReceiverRole() && message.getRoleChange().getReceiverRole() == SnakesProto.NodeRole.DEPUTY){
                synchronized (deputyMonitor){
                    deputyId = message.getReceiverId();
                }
            }
        }
    }

    @Override
    public void kickPlayer(int id) {
        if(serverLogic != null){
            serverLogic.kickPlayer(id);
        } else {
            SnakesProto.GamePlayer player = playersInfo.getPlayer(id);
            if (player == null){
                return;
            }
            SnakesProto.NodeRole role = player.getRole();
            if(role == SnakesProto.NodeRole.MASTER){
                kickMasterCase();
            } else {
                System.out.println("Клиент сделал таймаут не мастеру: " + role + "(это ок, если недавно был сервером)");
            }
        }
    }

    private void kickMasterCase(){
        synchronized (deputyMonitor){
            deputyId = tryToFindDeputy();
        }
        if(deputyId != null){
            networkHandler.changeMaster(deputyId);
        }
        if(serverLogic == null){
            deputyId = null;
        }
    }

    private Integer tryToFindDeputy(){
        for (SnakesProto.GamePlayer player : curState.getPlayers().getPlayersList()){
            if(player.getRole() == SnakesProto.NodeRole.DEPUTY){
                return player.getId();
            }
        }
        return null;
    }
    @Override
    public SnakesProto.GameMessage applyJoinMsg(SnakesProto.GameMessage joinMsg, int playerId) {
        if(serverLogic == null){
            return SnakesProto.GameMessage.newBuilder().setError(SnakesProto.GameMessage.ErrorMsg.newBuilder().setErrorMessage("Сэр, вы обознались.").build()).build();
        }
        return serverLogic.applyJoinMsg(joinMsg, playerId);
    }

    @Override
    public void applySteerMsg(SnakesProto.GameMessage message, Integer playerId) {
        if(serverLogic != null){
            serverLogic.applySteerMsg(message, playerId);
        }
    }
    @Override
    public void applyConfig(SnakesProto.GameConfig config) {
        if(serverLogic == null) {
            boolean isNewSize = this.config.getHeight() != config.getHeight() || this.config.getWidth() != config.getWidth();
            this.config.setFoodStatic(config.getFoodStatic());
            this.config.setWidth(config.getWidth());
            this.config.setHeight(config.getHeight());
            this.config.setStateDelayMs(config.getStateDelayMs());
            if(isNewSize) {
                gameField = new GameField(config.getWidth(), config.getHeight());
                view.recreateField(gameField);
            } else {
                gameField.cleanField();
            }
        }
    }

    @Override
    public Field getField() {
        return gameField;
    }
    @Override
    public void startServerWithNewGame() {
        if (serverLogic == null) {
            gameField = new GameField(config.getWidth(), config.getHeight());
            view.recreateField(gameField);
            playersInfo.clean();
            serverLogic = new ServerLogic();
            deputyId = null;
            serverLogic.startServerMode(SnakesProto.GameState.newBuilder().setStateOrder(0).setPlayers(SnakesProto.GamePlayers.newBuilder().build()).build());
        } else {
            System.out.println("Новая игра почему-то оказывается не новой...");
        }
    }

    @Override
    public void startServerWithExistedGame(int oldMaster, int newMaster) {
        if(serverLogic == null){
            serverLogic = new ServerLogic();
            serverLogic.kickPlayer(oldMaster);
            serverLogic.changeRole(SnakesProto.GameMessage.newBuilder()
                    .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder().setSenderRole(SnakesProto.NodeRole.MASTER).build()).setMsgSeq(-1).setSenderId(newMaster).setReceiverId(newMaster).build());
            serverLogic.chooseNewDeputyAndSendMsg();
            serverLogic.startServerMode(curState);
        }
    }

    @Override
    public void turnOffServerIfRun(){
        if (serverLogic != null) {
            serverLogic.turnOff();
            serverLogic = null;
        }
        curState = null;
    }

    @Override
    public void makeSelfJoin(int playerId){
        if(serverLogic != null){
            serverLogic.makeSelfJoin(playerId);
        }
    }

    private void updateGameState(SnakesProto.GameState gameState) {
        curState = gameState;
        gameField.updateField(gameState);
        playersInfo.updatePlayersAndSnakes(gameState);
        view.updateView(gameState.getPlayers());
    }

    private class ServerLogic {
        private Timer timerForGameCalcs = null;
        private Thread msgHandlerThread = null;
        private int curFoodCount = 0;
        private final ArrayList<PlayersInfo.PlayerWithSnake> playersToAdd = new ArrayList<>();
        private final ConcurrentHashMap<Integer, SnakesProto.Direction> rotationToApply = new ConcurrentHashMap<>();
        private final ArrayBlockingQueue<SnakesProto.GameMessage> rolesToApply = new ArrayBlockingQueue<>(QUEUE_SIZE);
        private final ArrayBlockingQueue<Integer> playersToKick = new ArrayBlockingQueue(QUEUE_SIZE);
        private final HashSet<Map.Entry<Integer, Integer>> foodCoord = new HashSet<>();

        public void startServerMode(SnakesProto.GameState startState) {

            if(msgHandlerThread == null){
                msgHandlerThread = new Thread(() -> {
                    Thread.currentThread().setName("Server msg handler ");
                    try {
                        msgHandlerJob();
                    } catch (InterruptedException e){
                        System.out.println("Server msg handler is down.");
                    }
                });
                msgHandlerThread.start();
            }

            if(timerForGameCalcs == null){
                curState = startState;
                List<SnakesProto.GameState.Coord> foodList = curState.getFoodsList();
                if(!curState.getFoodsList().isEmpty()){
                    for(SnakesProto.GameState.Coord coord : foodList){
                        foodCoord.add(new AbstractMap.SimpleEntry<Integer, Integer>(coord.getX(), coord.getY()));
                    }
                    curFoodCount=foodList.size();
                }
                stateOrder = curState.getStateOrder();
                timerForGameCalcs = new Timer("Timer recalculation thread");
                timerForGameCalcs.schedule(new TimerTask() {
                    @Override
                    public void run() {
                            networkHandler.sendState(recalculateState());
                            for(SnakesProto.GamePlayer player : curState.getPlayers().getPlayersList()){
                                System.out.println(player.getName() + " " + player.getId() + " " + player.getRole());
                            }
                        System.out.println("##################");
                    }
                }, 0, config.getStateDelayMs());
            }
        }
        public static final int QUEUE_SIZE = 256;
        private final ArrayBlockingQueue<SnakesProto.GameMessage> msgQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);

        private void msgHandlerJob() throws InterruptedException{
            Thread.currentThread().setName("Server listener thread.");
            SnakesProto.GameMessage message;
            while (true){
                if(Thread.currentThread().isInterrupted()){
                    throw new InterruptedException();
                }
                message = msgQueue.take();
                switch (message.getTypeCase()) {
                    case STEER -> rotatePlayer(message);
                    case ROLE_CHANGE -> {
                            changeRole(message);
                    }
                    default -> System.out.println("Server got invalid msg type.");
                }


            }
        }
        private void changeRole(SnakesProto.GameMessage message){
            rolesToApply.add(message);
        }

        public void applyChangeRoleMsg(SnakesProto.GameMessage message){
            serverLogic.msgQueue.add(message);
        }

        public void applySteerMsg(SnakesProto.GameMessage message, Integer playerId){
            serverLogic.msgQueue.add(SnakesProto.GameMessage.newBuilder(message).setSenderId(playerId).build());
        }

        public void rotatePlayer(SnakesProto.GameMessage msg){
                rotationToApply.put(msg.getSenderId(), msg.getSteer().getDirection());
        }

        public void kickPlayer(int id) {
            if(null != deputyId && id == deputyId){
                synchronized (deputyMonitor){
                    chooseNewDeputyAndSendMsg();
                    playersToKick.add(id);
                    return;
                }
            }
            playersToKick.add(id);
        }

        private void chooseNewDeputyAndSendMsg(){
            SnakesProto.GamePlayers players = curState.getPlayers();
            boolean found = false;
            for (SnakesProto.GamePlayer player : players.getPlayersList()){
                SnakesProto.NodeRole role = player.getRole();
                int id = player.getId();
                if(role == SnakesProto.NodeRole.NORMAL && id != deputyId){
                        deputyId = id;
                        found = true;
                        break;
                }
            }
            if(!found){
                deputyId = null;
            }else{
                networkHandler.sendChangeRoleToPlayer(SnakesProto.NodeRole.DEPUTY, deputyId);
                rolesToApply.add(SnakesProto.GameMessage.newBuilder()
                        .setRoleChange(
                                SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                        .setReceiverRole(SnakesProto.NodeRole.DEPUTY)
                                        .build()
                        )
                        .setReceiverId(deputyId)
                        .setMsgSeq(-1)
                        .build());
            }
        }

        public SnakesProto.GameMessage applyJoinMsg(SnakesProto.GameMessage msg, int playerId) {
            SnakesProto.GameMessage.JoinMsg joinMsg = msg.getJoin();
            SnakesProto.NodeRole askedRole = joinMsg.getRequestedRole();
            if(askedRole != SnakesProto.NodeRole.VIEWER && askedRole != SnakesProto.NodeRole.NORMAL){
                return SnakesProto.GameMessage.newBuilder().setMsgSeq(-1).setError(SnakesProto.GameMessage.ErrorMsg.newBuilder().setErrorMessage("Неправильная роль").build()).build();
            }
            synchronized (playersToAdd) {
                SnakesProto.GameState.Coord coord = findFreeSquare();
                if (coord.getX() == -1) {
                    return SnakesProto.GameMessage.newBuilder().setMsgSeq(-1).setError(SnakesProto.GameMessage.ErrorMsg.newBuilder().setErrorMessage("Ну соре места нет").build()).build();
                }
                SnakesProto.GamePlayer player;
                // синхронизация нужна из-за таймаут-потока
                if(askedRole == SnakesProto.NodeRole.NORMAL){
                    synchronized (deputyMonitor){
                        if(deputyId == null){
                            deputyId = playerId;
                            player = SnakesProto.GamePlayer.newBuilder().setId(playerId).setRole(SnakesProto.NodeRole.DEPUTY).setType(joinMsg.getPlayerType()).setName(joinMsg.getPlayerName()).setScore(0).build();
                        } else {
                            player = SnakesProto.GamePlayer.newBuilder().setId(playerId).setRole(askedRole).setType(joinMsg.getPlayerType()).setName(joinMsg.getPlayerName()).setScore(0).build();
                        }
                    }
                    SnakesProto.GameState.Snake snake = createSnakeForJoin(playerId, coord, player);
                    playersToAdd.add(new PlayersInfo.PlayerWithSnake(player, snake));
                } else {
                    player = SnakesProto.GamePlayer.newBuilder().setId(playerId).setRole(askedRole).setType(joinMsg.getPlayerType()).setName(joinMsg.getPlayerName()).setScore(0).build();
                    playersToAdd.add(new PlayersInfo.PlayerWithSnake(player, null));
                }
                return SnakesProto.GameMessage.newBuilder().setMsgSeq(msg.getMsgSeq()).setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build()).setReceiverId(playerId).build();
            }
        }

        int stateOrder = 0;
        private SnakesProto.GameState recalculateState(){
            SnakesProto.GameState result;
                for (Map.Entry<Integer, SnakesProto.Direction> entry : rotationToApply.entrySet()) {
                    rotationToApply.remove(entry.getKey(), entry.getValue());
                    Integer id = entry.getKey();
                    SnakesProto.GameState.Snake oldSnake = playersInfo.getSnake(id);
                    if (oldSnake == null || oldSnake.getState() == SnakesProto.GameState.Snake.SnakeState.ZOMBIE) {
                        continue;
                    }

                    SnakesProto.Direction newDirection = entry.getValue();
                    SnakesProto.Direction oldDirection = oldSnake.getHeadDirection();
                    if (newDirection == oldDirection
                            || (oldDirection == SnakesProto.Direction.UP && newDirection == SnakesProto.Direction.DOWN)
                            || (oldDirection == SnakesProto.Direction.DOWN && newDirection == SnakesProto.Direction.UP)
                            || (oldDirection == SnakesProto.Direction.LEFT && newDirection == SnakesProto.Direction.RIGHT)
                            || (oldDirection == SnakesProto.Direction.RIGHT && newDirection == SnakesProto.Direction.LEFT)) {
                        continue;
                    }
                    playersInfo.putSnake(id, SnakesProto.GameState.Snake.newBuilder(oldSnake).setHeadDirection(newDirection).build());
                }

                for (SnakesProto.GameMessage msg = rolesToApply.poll(); msg != null; msg = rolesToApply.poll()){
                    SnakesProto.GameMessage.RoleChangeMsg roleChangeMsg = msg.getRoleChange();
                    SnakesProto.NodeRole role;
                    int playerId;
                    if(roleChangeMsg.hasSenderRole()){
                        role = roleChangeMsg.getSenderRole();
                        playerId = msg.getSenderId();
                    } else {
                        role = roleChangeMsg.getReceiverRole();
                        playerId = msg.getReceiverId();
                    }
                    SnakesProto.GamePlayer player = playersInfo.getPlayer(playerId);
                    if(player == null){
                        continue;
                    }
                    if(player.getRole() == SnakesProto.NodeRole.MASTER){
                        continue;
                    }
                    playersInfo.putPlayer(SnakesProto.GamePlayer.newBuilder(player).setRole(role).build());
                    if(role == SnakesProto.NodeRole.VIEWER){
                        SnakesProto.GameState.Snake snake = playersInfo.getSnake(playerId);
                        if(snake != null){
                            playersInfo.putSnake(SnakesProto.GameState.Snake.newBuilder(snake).setState(SnakesProto.GameState.Snake.SnakeState.ZOMBIE).build());
                        }

                    }
                }
            // чтобы всегда по возможности заместитель был
            synchronized (deputyMonitor){
                for (Integer id = playersToKick.poll(); id != null; id = playersToKick.poll()){
                    playersInfo.removePlayerAndMakeSnakeZombie(id);
                }
            }
            // блочим добавление новых игроков, ибо идет перерасчет поля -> искать свободное место так-то нельзя.
            // ибо на нем может еда заспавниться.
            synchronized (playersToAdd) {
                ArrayList<SnakesProto.GameState.Snake> newSnakes = recalculateSnakes();
                SnakesProto.GameState.Builder stateBuilder = calculateCollision(newSnakes);
                for (PlayersInfo.PlayerWithSnake player : playersToAdd) {
                    playersInfo.putPlayer(player.player());
                    SnakesProto.GameState.Snake snake = player.snake();
                    if (null != snake) {
                        stateBuilder.addSnakes(player.snake());
                    }
                }
                playersToAdd.clear();


                SnakesProto.GamePlayers.Builder playersBuilder = SnakesProto.GamePlayers.newBuilder();
                SnakesProto.GamePlayer.Builder playerBuilder = SnakesProto.GamePlayer.newBuilder();
                for (SnakesProto.GamePlayer player : playersInfo.getPlayers().values()){
                    networkHandler.fillWithAddress(playerBuilder.clear().mergeFrom(player), player.getId());
                    playersBuilder.addPlayers(playerBuilder.build());
                }
                stateBuilder
                        .setStateOrder(++stateOrder)
                        .setPlayers(playersBuilder);


                result = stateBuilder.build();
                gameField.updateField(result);
                playersInfo.updateSnakes(result);

                createFood(stateBuilder);

                for(Map.Entry<Integer, Integer> entry : foodCoord){
                    stateBuilder.addFoods(SnakesProto.GameState.Coord.newBuilder().setX(entry.getKey()).setY(entry.getValue()).build());
                }

                result = stateBuilder.build();
                gameField.updateField(result);
            }
                curState = result;
                view.updateView(result.getPlayers());

            return result;
        }

        private SnakesProto.GameState.Builder calculateCollision(ArrayList<SnakesProto.GameState.Snake> newSnakes){
            HashMap<SnakesProto.GameState.Coord, Integer> heads = new HashMap<>();
            HashMap<Integer,SnakesProto.GameState.Snake> aliveSnakes = new HashMap<>();

            for (SnakesProto.GameState.Snake snake : newSnakes){
                SnakesProto.GameState.Coord headCoord = snake.getPoints(0);
                if(heads.containsKey(headCoord)){
                    aliveSnakes.remove(snake.getPlayerId());
                    aliveSnakes.remove(heads.get(headCoord));
                }else {
                    heads.put(headCoord, snake.getPlayerId());
                    aliveSnakes.put(snake.getPlayerId(), snake);
                }
            }

            heads.clear();
            for (SnakesProto.GameState.Snake snake : aliveSnakes.values()){
                heads.put(snake.getPoints(0),snake.getPlayerId());
            }

            HashSet<Integer> snakesToRemove = new HashSet<>();
            for (SnakesProto.GameState.Snake snake : aliveSnakes.values()){
                boolean isHead = true;
                SnakesProto.GameState.Coord.Builder absCoord = SnakesProto.GameState.Coord.newBuilder();
                for(SnakesProto.GameState.Coord coord : snake.getPointsList()){
                    if(isHead){
                        isHead = false;
                        absCoord.setX(coord.getX()).setY(coord.getY());
                        continue;
                    }
                    absCoord.setX(absCoord.getX() + coord.getX()).setY(absCoord.getY() + coord.getY());
                    SnakesProto.GameState.Coord res = absCoord.build();
                    if(heads.containsKey(res)){
                        int badGuyId = heads.get(res);
                        int victimId = snake.getPlayerId();
                        snakesToRemove.add(badGuyId);
                        if(badGuyId != victimId) {
                            SnakesProto.GamePlayer victim = playersInfo.getPlayer(victimId);
                            if(victim != null){
                                playersInfo.putPlayer(victimId, SnakesProto.GamePlayer.newBuilder(victim).setScore(victim.getScore() + 1).build());
                            }
                        }
                    }
                }
            }

            for (Integer badGuyId : snakesToRemove){
                aliveSnakes.remove(badGuyId);
            }

            return SnakesProto.GameState.newBuilder().addAllSnakes(aliveSnakes.values());
        }

        private ArrayList<SnakesProto.GameState.Snake> recalculateSnakes(){
            ArrayList<SnakesProto.GameState.Snake> newSnakes = new ArrayList<>();
            HashMap<Integer, SnakesProto.GameState.Snake> snakes = playersInfo.getSnakes();
            for (Map.Entry<Integer, SnakesProto.GameState.Snake> entry : snakes.entrySet()){
                SnakesProto.GameState.Snake snake = entry.getValue();
                int playerId = entry.getKey();
                SnakesProto.GameState.Snake movedSnake = gameField.recalculateSnake(snake);
                Integer x = movedSnake.getPoints(0).getX();
                Integer y = movedSnake.getPoints(0).getY();
                if(gameField.getTitle(x,y).isFood()){
                    SnakesProto.GamePlayer player = playersInfo.getPlayer(playerId);
                    if(player!= null){
                        SnakesProto.GamePlayer.Builder playerBuilder = SnakesProto.GamePlayer.newBuilder(player);
                        playerBuilder.setScore(playerBuilder.getScore() + 1);
                        playersInfo.putPlayer(playerId, playerBuilder.build());
                    }
                    --curFoodCount;
                    foodCoord.remove(new AbstractMap.SimpleEntry<>(x,y));
                }
                newSnakes.add(movedSnake);
            }
            return newSnakes;
        }

        private final Random random = new Random();
        private void createFood(SnakesProto.GameState.Builder stateBuilder){
            int foodToSpawn = playersInfo.getNumAliveSnakes() + config.getFoodStatic() - curFoodCount;
            for(Iterator<Map.Entry<Integer, Integer>> iter = foodCoord.iterator(); foodToSpawn < 0; foodToSpawn++){
                iter.next();
                iter.remove();
                curFoodCount--;
            }
            int numTries = 0;
            while (foodToSpawn > 0){
                int placeX = random.nextInt(config.getWidth());
                int placeY = random.nextInt(config.getHeight());
                FieldTitle curTitle =gameField.getTitle(placeX, placeY);
                if(curTitle.isEmpty()){
                    numTries = 0;
                    stateBuilder.addFoods(SnakesProto.GameState.Coord.newBuilder().setX(placeX).setY(placeY).build());
                    --foodToSpawn;
                    ++curFoodCount;
                    foodCoord.add(new AbstractMap.SimpleEntry<>(placeX,placeY));
                } else {
                    numTries++;
                }
                // если половина поля занята, то вероятность такого события ~0.01%, меняем стратегию поиска свободного места.
                if(numTries > 9){
                    int maxX = gameField.getSizeX();
                    int maxY = gameField.getSizeY();
                    while (foodToSpawn > 0){
                        for (int y = 0; y < maxY; y++){
                            for (int x = 0; x < maxX; x++){
                                if(curTitle.isEmpty()){
                                    stateBuilder.addFoods(SnakesProto.GameState.Coord.newBuilder().setX(placeX).setY(placeY).build());
                                    --foodToSpawn;
                                    ++curFoodCount;
                                    foodCoord.add(new AbstractMap.SimpleEntry<>(placeX,placeY));
                                }
                            }
                        }
                        // если всё поле прошли, то и места для спавна нет.
                        foodToSpawn = 0;
                    }
                }
            }
        }

        private SnakesProto.GameState.Snake createSnakeForJoin(int playerId, SnakesProto.GameState.Coord coord, SnakesProto.GamePlayer player) {
            return SnakesProto.GameState.Snake.newBuilder().setState(SnakesProto.GameState.Snake.SnakeState.ALIVE).setHeadDirection(SnakesProto.Direction.DOWN).setPlayerId(playerId)
                    .addPoints(0, coord)
                    .addPoints(1, SnakesProto.GameState.Coord.newBuilder().setY(-1).setX(0).build())
                    .build();
        }

        private SnakesProto.GameState.Coord findFreeSquare(){
            int sizeX = gameField.getSizeX();
            int sizeY = gameField.getSizeY();
            SnakesProto.GameState.Coord coords = null;
            boolean goodPlace = false;
            for(int y = 0; y < sizeY; y++){
                goodPlace = true;
                for (int x = 0; x < sizeX; x++){
                    goodPlace = true;
                    coords = SnakesProto.GameState.Coord.newBuilder().setX(x-1).setY(y-1).build();
                    for (int i = 0; i < 5 && goodPlace; i++){
                        coords = gameField.getCoordByDirection(SnakesProto.Direction.DOWN, coords);
                        SnakesProto.GameState.Coord bufferCoords = SnakesProto.GameState.Coord.newBuilder(coords).build();
                        for (int j = 0; j < 5 && goodPlace; j++){
                            bufferCoords = gameField.getCoordByDirection(SnakesProto.Direction.RIGHT, bufferCoords);
                            if(!gameField.getTitle(bufferCoords).isEmpty()){
                                goodPlace = false;
                            }
                        }
                    }
                    if(goodPlace){
                        break;
                    }
                }
                if(goodPlace){
                    break;
                }
            }

            if(!goodPlace){
                System.out.println("No good place");
                return SnakesProto.GameState.Coord.newBuilder()
                        .setX(-1)
                        .setY(-1)
                        .build();
            }

            System.out.println("Good place: " + (coords.getX() + 3) % sizeX + " AND " +   (coords.getY() - 1) % sizeY);
            return SnakesProto.GameState.Coord.newBuilder()
                    .setX((coords.getX() + 3) % sizeX)
                    .setY((coords.getY() - 1) % sizeY)
                    .build();
        }


        public void makeSelfJoin(int playerId){
            synchronized (playersToAdd) {
                SnakesProto.GameState.Coord coord = findFreeSquare();

                SnakesProto.GamePlayer player = SnakesProto.GamePlayer.newBuilder()
                        .setName(Config.getInstance().getPlayerName())
                        .setType(SnakesProto.PlayerType.HUMAN)
                        .setId(playerId)
                        .setScore(0)
                        .setRole(SnakesProto.NodeRole.MASTER)
                        .build();

                playersToAdd.add(new PlayersInfo.PlayerWithSnake(player, createSnakeForJoin(playerId, coord, player)));
            }
            
        }

        public void turnOff(){
            if(timerForGameCalcs != null) {
                timerForGameCalcs.cancel();
                timerForGameCalcs = null;
            }
            if(msgHandlerThread != null){
                msgHandlerThread.interrupt();
                msgHandlerThread = null;
            }
            playersInfo.clean();
        }
    }


}
