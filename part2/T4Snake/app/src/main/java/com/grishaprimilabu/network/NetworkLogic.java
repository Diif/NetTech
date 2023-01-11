package com.grishaprimilabu.network;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.grishaprimilabu.model.Model;
import com.grishaprimilabu.model.entities.Config;
import com.grishaprimilabu.network.transport.SimpleTransport;
import com.grishaprimilabu.network.transport.TransportLayer;
import com.grishaprimilabu.proto.SnakesProto;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkLogic implements NetworkHandler {

    private Model model;
    private TransportLayer socket;
    private final ConcurrentHashMap<String, InetSocketAddress> gamesToConnect = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SnakesProto.GameConfig> gamesConfigs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, InetSocketAddress> playersAddressesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Integer> playersIdByAddresses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Boolean> aliveConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Boolean> notToPingConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Long> validSeqs = new ConcurrentHashMap<>();
    private int nextId;
    private int ownId;
    private final AtomicLong ownSeq = new AtomicLong(0);
    private Timer announcementTimer;
    private Timer timeoutTimer;
    private Timer pingAndResendTimer;
    private Thread multicastListenerThread;
    private Thread listeningThread;
    private enum NetworkState{
        SERVER, NONE, JOINING, LOADING, PLAYING, LISTENING
    }
    private NetworkState state = NetworkState.NONE;
    private InetSocketAddress masterAddr;
    private int masterId = -1;

    public void init(Model model){
        this.model = model;
        try {
            socket = new SimpleTransport();
        } catch ( Exception e){
            System.out.println(e.getLocalizedMessage());
        }
        listeningThread = new Thread(() -> {
            try {
                Thread.currentThread().setName("Network listener");
                listeningJob();
            } catch (InterruptedException e){
                System.out.println("Network listener is down");
            }
        });
        listeningThread.start();

    }

    private void listeningJob() throws InterruptedException{
        while (true){
            try {
//                DatagramPacket packet = receiveUnicastWithValidSeq();
                DatagramPacket packet = socket.receiveUnicast();
                if(Thread.currentThread().isInterrupted()){
                    throw new InterruptedException();
                }
                if(state == NetworkState.NONE){
                    continue;
                }
                SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(packet.getData());
                InetSocketAddress address = (InetSocketAddress) packet.getSocketAddress();
                switch (message.getTypeCase()){
                    case PING -> {
                        if(playersIdByAddresses.containsKey(address)){
                            aliveConnections.put(address, true);
                            sendAck(message.getMsgSeq(), address);
                        }
                    }
                    case ROLE_CHANGE -> {
                        aliveConnections.put(address, true);
                        sendAck(message.getMsgSeq(), address);
                        if(checkAndUpdateSeq(message.getMsgSeq(), address)){
                            model.applyChangeRoleMsg(message);
                        }
                    }
                    case STATE -> {
                        System.out.println("Словил стейт пакет");
                        if(address.equals(masterAddr) && state == NetworkState.PLAYING) {
                            System.out.println("Пакет от нужного чела");
                            aliveConnections.put(address, true);
                            sendAck(message.getMsgSeq(), address);
                            if(model.getCurState() == null){
                                System.out.println("Пакет балдежный");
                                model.applyGameStateMsg(message.getState().getState());
                            }else if(message.getState().getState().getStateOrder() > model.getCurState().getStateOrder()){
                                System.out.println("Пакет балдежный");
                                model.applyGameStateMsg(message.getState().getState());
                            }
                        }
                    }
                    case STEER -> {
                        if(state == NetworkState.SERVER && playersIdByAddresses.containsKey(address)) {
                            aliveConnections.put(address, true);
                            sendAck(message.getMsgSeq(), address);
                            if(checkAndUpdateSeq(message.getMsgSeq(), address)){
                                model.applySteerMsg(message, playersIdByAddresses.get(address));
                            }
                        }
                    }
                    // самая тяжелая операция, на аутсорс её, чтобы не зависнуть
                    case JOIN -> {
                        CompletableFuture.supplyAsync(() -> model.applyJoinMsg(message, nextId++)).thenAccept((msg) -> sendJoinAnswer(msg, address, message.getMsgSeq()));
                    }
                    case ACK -> {
                        if(state == NetworkState.JOINING){
                            ownId = message.getReceiverId();
                            masterId = message.getSenderId();
                            masterAddr = address;
                            state = NetworkState.LOADING;
                            joinResult.add(message);
                            playersIdByAddresses.put(address, masterId);
                            playersAddressesById.put(masterId, address);
                            aliveConnections.put(address, true);
                        } else if (playersIdByAddresses.containsKey(address)){
                            int senderId = message.getSenderId();
                            if(senderId == masterId && null !=notAckedMsgToMaster && notAckedMsgToMaster.getKey() == message.getMsgSeq()){
                                notAckedMsgToMaster = null;
                                synchronized (objectForAckWaiting){
                                    objectForAckWaiting.notifyAll();
                                }
                            }else {
                                Map.Entry<Long, DatagramPacket> entry = notAckedPings.get(senderId);
                                if (null != entry) {
                                    if (message.getMsgSeq() == entry.getKey()) {
                                        notAckedPings.remove(senderId);
                                    }
                                } else if (state == NetworkState.SERVER) {
                                    entry = notAckedMsgsForAll.get(senderId);
                                    if (null != entry && message.getMsgSeq() == entry.getKey()) {
                                        synchronized (notAckedMsgsForAll){
                                            notAckedMsgsForAll.remove(senderId);
                                            if (notAckedMsgsForAll.isEmpty()) {
                                                notAckedMsgsForAll.notifyAll();
                                            }
                                        }
                                    }
                                }
                            }
                            aliveConnections.put(address, true);
                        }
                    }
                    case DISCOVER -> {
                        if (state == NetworkState.SERVER) {
                            CompletableFuture.runAsync(() -> {
                                SnakesProto.GameAnnouncement announcement = SnakesProto.GameAnnouncement.newBuilder()
                                        .setCanJoin(true)
                                        .setPlayers(model.getCurState().getPlayers())
                                        .setConfig(Config.getInstance().getGameConfigMsg())
                                        .setGameName(Config.getInstance().getGameName())
                                        .build();
                                SnakesProto.GameMessage.AnnouncementMsg announcementMsg = SnakesProto.GameMessage.AnnouncementMsg.newBuilder().addGames(announcement).build();
                                SnakesProto.GameMessage message1 = SnakesProto.GameMessage.newBuilder().setMsgSeq(-1).setAnnouncement(announcementMsg).build();
                                try {
                                    sendWithoutAck(message1, address);
                                } catch (IOException e){
                                    System.out.println("Не получилось отправить ответ на discover");
                                }
                            });
                        }
                    }
                    case ANNOUNCEMENT -> {
                        if(state == NetworkState.LISTENING){
                            try {
                                announcementMsg.add(packet);
                            } catch (Exception e){
                                System.out.println(e.getLocalizedMessage());
                            }
                        }
                    }
                }
            }
            catch (SocketTimeoutException ignored){}
            catch (IOException e){
                if(Thread.currentThread().isInterrupted()){
                    throw new InterruptedException();
                }
                System.out.println(e.getLocalizedMessage());
            }

        }
    }

    private boolean checkAndUpdateSeq(long seq, InetSocketAddress address){
        Long validSeq = validSeqs.get(address);
        if(null != validSeq){
            if(seq < validSeq){
                return false;
            } else {
                validSeqs.put(address, seq + 1);
            }
        } else {
            validSeqs.put(address, seq + 1);
        }
        return true;
    }

    private boolean skipTimeout = false;
    private void timeoutTimerJob(){
        if(skipTimeout){
            skipTimeout = false;
            return;
        }
        for(Map.Entry<InetSocketAddress, Integer> entry : playersIdByAddresses.entrySet()){
            InetSocketAddress address = entry.getKey();
            if(!aliveConnections.containsKey(address)){
                int playerId = entry.getValue();
                System.out.println("Игрок словил таймаут: " + playerId);
                playersAddressesById.remove(playerId);
                playersIdByAddresses.remove(address);
                validSeqs.remove(address);
                model.kickPlayer(playerId);
                notAckedMsgsForAll.remove(playerId);
                notAckedPings.remove(playerId);
                synchronized (notAckedMsgsForAll){
                    if(notAckedMsgsForAll.isEmpty()){
                        notAckedMsgsForAll.notifyAll();
                    }
                }
            }
        }
        aliveConnections.clear();
    }

    private final ConcurrentHashMap<Integer, Map.Entry<Long, DatagramPacket>> notAckedPings = new ConcurrentHashMap<>();
    private void pingAndResendTimerJob(){
        //переотправка пакета мастеру
        Map.Entry<Long, DatagramPacket> masterEntry = notAckedMsgToMaster;
        if(null != masterEntry){
            if(state == NetworkState.SERVER || state == NetworkState.LOADING){
                synchronized (objectForAckWaiting){
                    objectForAckWaiting.notifyAll();
                }
            } else if(state == NetworkState.PLAYING){
                try {
                    System.out.println("Пакет пересылаем мастеру");
                    socket.sendUnicast(masterEntry.getValue());
                    notToPingConnections.put((InetSocketAddress) masterEntry.getValue().getSocketAddress(), true);
                } catch (IOException e){
                    System.out.println("Не удалось переотправить пакет мастеру: " + e.getLocalizedMessage());
                }
            } else {
                notAckedMsgToMaster = null;
            }
        }

        //переотправка пакетов "мультикаст"-рассылки. Это только у сервера будет работать
        for (Map.Entry<Integer, Map.Entry<Long, DatagramPacket>> fullEntry : notAckedMsgsForAll.entrySet()){
            try {
                Map.Entry<Long, DatagramPacket> entry = fullEntry.getValue();
                InetSocketAddress address = (InetSocketAddress) entry.getValue().getSocketAddress();
                if(!playersIdByAddresses.containsKey(address)){
                    synchronized (notAckedMsgsForAll){
                        notAckedMsgsForAll.remove(fullEntry.getKey());
                        if(notAckedMsgsForAll.isEmpty()){
                            notAckedMsgsForAll.notifyAll();
                        }
                    }
                    continue;
                }
                System.out.println("Делаем ресенд стейта чудикам.");
                DatagramPacket packet = entry.getValue();
                socket.sendUnicast(packet);
                notToPingConnections.put((InetSocketAddress) packet.getSocketAddress(), true);
            } catch (IOException e){
                System.out.println("Ресендер не смог переотправить мультикаст-пакет: " + e.getLocalizedMessage());
            }
        }

        //переотправка неакнутых пингов.
        for (Map.Entry<Long, DatagramPacket> entry : notAckedPings.values()){
            try {
                DatagramPacket packet = entry.getValue();
                socket.sendUnicast(packet);
                notToPingConnections.put((InetSocketAddress) packet.getSocketAddress(), true);
            } catch (IOException e){
                System.out.println("Ресендер не смог переотправить пинг: " + e.getLocalizedMessage());
            }
        }

        //отправка новых пингов
        for(Map.Entry<InetSocketAddress, Integer> entry : playersIdByAddresses.entrySet()){
            InetSocketAddress address = entry.getKey();
            if(!notToPingConnections.containsKey(address)){
                try {
                    long seq = ownSeq.getAndIncrement();
                    byte[] data = SnakesProto.GameMessage.newBuilder()
                            .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                            .setMsgSeq(seq)
                            .build()
                            .toByteArray();
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    packet.setSocketAddress(address);
                    notAckedPings.put(entry.getValue(), new AbstractMap.SimpleEntry<>(seq, packet));
                    socket.sendUnicast(packet);
                } catch (IOException e){
                    System.out.println("Не получилось отправить пинг: " + e.getLocalizedMessage());
                }
            }
        }
        notToPingConnections.clear();
    }

    private void sendJoinAnswer(SnakesProto.GameMessage message, InetSocketAddress address, Long seq){
        if(message.hasError()){
            try {
                sendWithoutAck(SnakesProto.GameMessage.newBuilder(message).setMsgSeq(seq).build(), address);
            } catch (IOException e){
                System.out.println(e.getLocalizedMessage());
            }
        } else{
            Integer newId = message.getReceiverId();
            playersIdByAddresses.put(address, newId);
            playersAddressesById.put(newId, address);
            aliveConnections.put(address, true);
            try {
                sendWithoutAck(SnakesProto.GameMessage.newBuilder(message).setMsgSeq(seq).setSenderId(ownId).build(), address);
            } catch (IOException e){
                System.out.println(e.getLocalizedMessage());
            }
        }
    }

    public void sendMulticastGameAnnouncement(SnakesProto.GameMessage.AnnouncementMsg announcementMsg) {
        try {
            socket.sendMulticast(msgToPacket(SnakesProto.GameMessage.newBuilder()
                    .setMsgSeq(ownSeq.getAndIncrement())
                    .setAnnouncement(announcementMsg).build()));
        } catch (IOException e){
            System.out.println(e.getLocalizedMessage());
        }
    }

    @Override
    public void sendRotation(SnakesProto.Direction direction) {
        if(state == NetworkState.SERVER){
            model.applySteerMsg(SnakesProto.GameMessage.newBuilder()
                    .setSteer(SnakesProto.GameMessage.SteerMsg.newBuilder().setDirection(direction).build()).setMsgSeq(-1).build(), ownId);
        } else if(state == NetworkState.PLAYING){
            sendWithAck(SnakesProto.GameMessage.newBuilder()
                    .setSteer(SnakesProto.GameMessage.SteerMsg.newBuilder().setDirection(direction).build()).setMsgSeq(-1).build(), masterAddr);
        }
    }

    //здесь надо либо через тредпул таски кидать, либо блочить поток перерасчета. Как-то даже хз, какой вариант лучше.
    //вариант с тредпулом может резко ускорить игру после таймаута)0)0))).
    private final ExecutorService stateSender = Executors.newFixedThreadPool(1);
    {
        stateSender.submit(() -> Thread.currentThread().setName("State sender thread"));
    }
    @Override
    public void sendState(SnakesProto.GameState gameState) {
        // в тредпуле есть очередь, и так как поток один, то порядок выполнения гарантируется.
        stateSender.submit(() -> sendWithAckForAll(SnakesProto.GameMessage.newBuilder().setState(SnakesProto.GameMessage.StateMsg.newBuilder().setState(gameState).build()).setMsgSeq(-1).build()));
    }

    @Override
    public void fillWithAddress(SnakesProto.GamePlayer.Builder builder, int playerId) {
        InetSocketAddress address = playersAddressesById.get(playerId);
        if(address == null){
            return;
        }
        builder.setPort(address.getPort()).setIpAddressBytes(ByteString.copyFrom(address.getAddress().getAddress()));
    }

    @Override
    public void sendChangeRoleToViewToMaster() {
        sendChangeRoleToPlayer(SnakesProto.NodeRole.VIEWER, masterId);
    }

    @Override
    public void sendChangeRoleToPlayer(SnakesProto.NodeRole role, int playerId) {
        if((state == NetworkState.SERVER || state == NetworkState.LOADING) && playerId == ownId){
            model.applyChangeRoleMsg(SnakesProto.GameMessage.newBuilder()
                    .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder().setSenderRole(role).build()).setMsgSeq(-1).setSenderId(ownId).setReceiverId(ownId).build());
        } else if (state == NetworkState.SERVER || state == NetworkState.LOADING){
            sendWithAck(SnakesProto.GameMessage.newBuilder()
                    .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder().setReceiverRole(role).build()).setMsgSeq(-1).setSenderId(ownId).setReceiverId(playerId).build(), playersAddressesById.get(playerId));
        }else if(state == NetworkState.PLAYING){
            sendWithAck(SnakesProto.GameMessage.newBuilder()
                    .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder().setSenderRole(SnakesProto.NodeRole.VIEWER).build()).setMsgSeq(-1).setSenderId(ownId).setReceiverId(masterId).build(), masterAddr);

        }
    }

    SynchronousQueue<SnakesProto.GameMessage>  joinResult = new SynchronousQueue<>();
    @Override
    public SnakesProto.GameMessage connectToGame(String gameName) {
        if(gamesToConnect.containsKey(gameName)){
            InetSocketAddress address = gamesToConnect.get(gameName);
            SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                    .setJoin(SnakesProto.GameMessage.JoinMsg.newBuilder()
                            .setGameName(gameName)
                            .setPlayerName(Config.getInstance().getPlayerName())
                            .setRequestedRole(Config.getInstance().getRole())
                            .setPlayerType(SnakesProto.PlayerType.HUMAN)
                            .build()).setMsgSeq(-1).build();
            try {
                state = NetworkState.JOINING;
                sendWithoutAck(message, address);
                SnakesProto.GameMessage anw = joinResult.poll(NetworkConfig.TIMEOUT_TIME_MLS, TimeUnit.MILLISECONDS);
                if(anw == null){
                    return SnakesProto.GameMessage.newBuilder().setError(SnakesProto.GameMessage.ErrorMsg.newBuilder().setErrorMessage("Не удалось подключиться к серверу (timeout)").build()).setMsgSeq(-1).build();
                } else if(anw.hasError()){
                    return anw;
                }
                if(anw.hasAck()){
                    model.applyConfig(gamesConfigs.get(gameName));
                    Config.getInstance().setGameName(gameName);
                    createTimeoutAndPingTimers();
                    state = NetworkState.PLAYING;
                    return anw;
                }
            } catch (Exception e){
                System.out.println(e.getLocalizedMessage());
            }
        }
        return SnakesProto.GameMessage.newBuilder().setError(SnakesProto.GameMessage.ErrorMsg.newBuilder().setErrorMessage("Не удалось подключиться к серверу").build()).setMsgSeq(-1).build();
    }

    @Override
    public void askServerForGames(String ip, int port) {
        try {
            sendWithoutAck(SnakesProto.GameMessage.newBuilder().setDiscover(SnakesProto.GameMessage.DiscoverMsg.newBuilder().build()).setMsgSeq(-1).build(), new InetSocketAddress(ip, port));
        } catch (IOException e){
            System.out.println(e.getLocalizedMessage());
        }
    }


    @Override
    public void changeMaster(int deputyId) {
        state = NetworkState.LOADING;
        validSeqs.clear();
        skipTimeout = true;
        SnakesProto.GamePlayers players = model.getCurState().getPlayers();
        if(deputyId == ownId){
            for(SnakesProto.GamePlayer player : players.getPlayersList()){
                try {
                    if(player.getId() >= nextId){
                        nextId = player.getId()+1;
                    }
                    if(player.getRole() == SnakesProto.NodeRole.MASTER || player.getRole() == SnakesProto.NodeRole.DEPUTY){
                        continue;
                    }
                    InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(player.getIpAddressBytes().toByteArray()), player.getPort());
                    int id = player.getId();
                    aliveConnections.put(address, true);
                    playersIdByAddresses.put(address,id);
                    playersAddressesById.put(id, address);
                } catch (UnknownHostException e){
                    System.out.println("Депутя не смог понять адрес: " + Arrays.toString(player.getIpAddressBytes().toByteArray()));
                }
            }
            int oldMaster = masterId;
            masterId = ownId;
            model.startServerWithExistedGame(oldMaster, masterId);
            state = NetworkState.SERVER;
            createAnnouncementTimer();
        } else {
            for(SnakesProto.GamePlayer player : players.getPlayersList()){
                try {
                    if(player.getRole() == SnakesProto.NodeRole.DEPUTY){
                        InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(player.getIpAddressBytes().toByteArray()), player.getPort());
                        masterId = deputyId;
                        masterAddr = address;
                        aliveConnections.put(address, true);
                        playersIdByAddresses.put(address,deputyId);
                        playersAddressesById.put(deputyId, address);
                        break;
                    }
                } catch (UnknownHostException e){
                    System.out.println("не получилось разрезолвить адресс депути: " + Arrays.toString(player.getIpAddressBytes().toByteArray()));
                }
            }
            state = NetworkState.PLAYING;
        }
    }


    private void createTimeoutAndPingTimers(){
        if(timeoutTimer != null){
            timeoutTimer.cancel();
        }
        if(pingAndResendTimer != null){
            pingAndResendTimer.cancel();
        }
        timeoutTimer = new Timer("Timer timeout");
        pingAndResendTimer = new Timer("Timer ping");

        timeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeoutTimerJob();
            }
        },NetworkConfig.TIMEOUT_TIME_MLS, NetworkConfig.TIMEOUT_TIME_MLS);

        pingAndResendTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                pingAndResendTimerJob();
            }
        },0, NetworkConfig.PING_TIME_MLS);
    }




    private void sendAck(long seq, InetSocketAddress address){
        Integer id = playersIdByAddresses.get(address);
        if(null == id){
            return;
        }
        byte[] data = SnakesProto.GameMessage.newBuilder()
                .setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build())
                .setSenderId(ownId)
                .setReceiverId(id)
                .setMsgSeq(seq)
                .build()
                .toByteArray();
        DatagramPacket packet = new DatagramPacket(data, data.length);
        try {
            socket.sendUnicast(packet, address);
        } catch (IOException e){
            System.out.println("Ack не отправляется: " + e.getLocalizedMessage());
        }
    }
    private void sendWithoutAck(SnakesProto.GameMessage message, InetSocketAddress address) throws IOException {
        DatagramPacket packet = msgToPacket(SnakesProto.GameMessage.newBuilder(message).setMsgSeq(ownSeq.getAndIncrement()).build());
        notToPingConnections.put(address, true);
        socket.sendUnicast(packet, address);
    }

    //т.к. в протоколе ничего про окно не говорится, то отправлять может максимум 1 сообщение.
    private Map.Entry<Long, DatagramPacket> notAckedMsgToMaster;
    private final Object objectForAckWaiting = new Object();
    //нужно для избежания лока со стороны графики + обеспечить порядок выполнения
    private final ExecutorService toMasterSender = Executors.newFixedThreadPool(1);
    {
        toMasterSender.submit(() -> Thread.currentThread().setName("To master sender thread"));
    }

    private void sendWithAck(SnakesProto.GameMessage message, InetSocketAddress address){
        toMasterSender.submit(() -> {
            long seq = ownSeq.getAndIncrement();
            DatagramPacket packet = msgToPacket(SnakesProto.GameMessage.newBuilder(message).setMsgSeq(seq).build());
            packet.setSocketAddress(address);
            notToPingConnections.put(address, true);
            notAckedMsgToMaster = new AbstractMap.SimpleEntry<>(seq, packet);
            try {
                socket.sendUnicast(packet);
            } catch (IOException e) {
                System.out.println("SendWithAck не смог отправить пакет: " + e.getLocalizedMessage());
            }
            while (null != notAckedMsgToMaster) {
                try {
                    if(state == NetworkState.SERVER || state == NetworkState.LOADING){
                        switch (message.getTypeCase()){
                            case STEER -> {
                                model.applySteerMsg(SnakesProto.GameMessage.newBuilder()
                                        .setSteer(SnakesProto.GameMessage.SteerMsg.newBuilder().setDirection(message.getSteer().getDirection()).build()).setMsgSeq(-1).build(), ownId);
                            }
                            case ROLE_CHANGE -> System.out.println("Смена роли на VIEWER невозможна.");
                            default -> System.out.println("Невалидное сообщение мастеру было обнаружено во время рехоста.");
                        }
                        notAckedMsgToMaster = null;
                        break;
                    }
                    synchronized (objectForAckWaiting) {
                        objectForAckWaiting.wait();
                    }
                } catch (InterruptedException e) {
                    System.out.println("Поток был прерван во время ожидания ака от мастера: " + e.getLocalizedMessage());
                    return;
                }
            }
        });
    }

    private final ConcurrentHashMap<Integer, Map.Entry<Long, DatagramPacket>> notAckedMsgsForAll = new ConcurrentHashMap<>();

    private void sendWithAckForAll(SnakesProto.GameMessage message) {
        System.out.println("Начинаю рассылку стейта");
        SnakesProto.GameMessage.Builder builder = SnakesProto.GameMessage.newBuilder(message);
        for (Map.Entry<Integer, InetSocketAddress> entry : playersAddressesById.entrySet()){
            long seq = ownSeq.getAndIncrement();
            builder.setMsgSeq(seq);
            InetSocketAddress address = entry.getValue();
            int playerId = entry.getKey();
            byte[] data = builder.build().toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length);
            packet.setSocketAddress(address);
            try {
                notAckedMsgsForAll.put(playerId,new AbstractMap.SimpleEntry<>(seq, packet));
                socket.sendUnicast(packet);
            } catch (IOException e){
                System.out.println("Юникаст рассылка не смогла отправить пакет на адрес: " + address.getAddress().toString() + ":" + address.getPort());
            }
        }

        //поток для ресенда будет пересылать неакнутые пакеты, поток-слушатель будет их отсюда выкидывать + будить этот поток
        //поток с таймаутом тоже будет выкидывать неакнутые сообщения и будить этот поток + будет постоянно будить при пустой
        //мапе, т.к. иначе есть шанс заснуть навсегда
        synchronized (notAckedMsgsForAll) {
            while (!notAckedMsgsForAll.isEmpty()) {
                try {
                    notAckedMsgsForAll.wait();
                } catch (InterruptedException e) {
                    System.out.println("Рассылающий поток был остановлен: " + e.getLocalizedMessage());
                    return;
                }
            }
        }
        System.out.println("Заканчиваю рассылку стейта");

    }

    private final ArrayBlockingQueue<DatagramPacket> announcementMsg = new ArrayBlockingQueue<>(1);
    @Override
    public void startListeningForGames(ObservableList<String> listForGames){
        if(multicastListenerThread == null){
            gamesToConnect.clear();
            gamesConfigs.clear();
            state = NetworkState.LISTENING;
            multicastListenerThread = new Thread( () -> {
                DatagramPacket packet = null;
                while (!Thread.currentThread().isInterrupted()){
                    try {
                        while (packet == null){
                             packet = socket.receiveMulticast();
                             if(Thread.currentThread().isInterrupted()){
                                 return;
                             }
                        }
                        InetSocketAddress address = (InetSocketAddress) packet.getSocketAddress();

                        SnakesProto.GameMessage gameMessage = SnakesProto.GameMessage.newBuilder().mergeFrom(packet.getData()).build();
                        if(gameMessage.hasAnnouncement()){
                            SnakesProto.GameMessage.AnnouncementMsg announcementMsg = gameMessage.getAnnouncement();
                            for (SnakesProto.GameAnnouncement announcement : announcementMsg.getGamesList()) {
                                String gameName = announcement.getGameName();
                                if (!gamesToConnect.containsKey(gameName)){
                                    Platform.runLater(() -> listForGames.add(gameName));
                                }
                                gamesConfigs.put(gameName, announcement.getConfig());
                                gamesToConnect.put(gameName, address);
                            }
                        }
                        packet = null;
                    } catch (SocketTimeoutException e){
                        packet = announcementMsg.poll();
                    } catch (IOException e1){
                        System.out.println(e1.getLocalizedMessage());
                    }
                }
            });
            multicastListenerThread.start();
        }

    }

    @Override
    public void stopListeningForGames(){
        if(multicastListenerThread != null){
            multicastListenerThread.interrupt();
            multicastListenerThread = null;
            if(state == NetworkState.LISTENING){
                state = NetworkState.NONE;
            }
        }
    }
    @Override
    public void hostNewGame(){
        gamesToConnect.clear();
        playersAddressesById.clear();
        playersIdByAddresses.clear();
        aliveConnections.clear();
        cleanResendFields();
        gamesConfigs.clear();
        notToPingConnections.clear();
        nextId = 0;
        ownId = nextId++;
        masterId = ownId;
        validSeqs.clear();
        ownSeq.set(0);

        state = NetworkState.SERVER;

        createAnnouncementTimer();
        createTimeoutAndPingTimers();

        model.makeSelfJoin(ownId);
    }

    private void createAnnouncementTimer(){
        if(announcementTimer == null){
            announcementTimer = new Timer("Announcement timer");

            announcementTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    sendMulticastGameAnnouncement(SnakesProto.GameMessage.AnnouncementMsg.newBuilder()
                            .addGames(SnakesProto.GameAnnouncement.newBuilder()
                                    .setConfig(Config.getInstance().getGameConfigMsg())
                                    .setGameName(Config.getInstance().getGameName())
                                    .setPlayers(
                                            SnakesProto.GamePlayers.newBuilder().
                                                    addAllPlayers(model.getCurState().getPlayers().getPlayersList())
                                                    .build())
                                    .build())
                            .build());
                }
            },NetworkConfig.ANNOUNCEMENT_DELAY_MLS, NetworkConfig.ANNOUNCEMENT_DELAY_MLS);
        }
    }

    private DatagramPacket msgToPacket(SnakesProto.GameMessage message){
        byte[] data = message.toByteArray();
        return new DatagramPacket(data, data.length);
    }

    @Override
    public void disconnect(){
        if(announcementTimer != null) {
            announcementTimer.cancel();
            announcementTimer = null;
        }
        if(timeoutTimer != null){
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
        if(pingAndResendTimer != null){
            pingAndResendTimer.cancel();
            pingAndResendTimer = null;
        }
        cleanResendFields();
        state = NetworkState.NONE;
    }

    private void cleanResendFields(){
        notAckedMsgToMaster = null;
        notAckedMsgsForAll.clear();
        notAckedPings.clear();
        synchronized (objectForAckWaiting){
            objectForAckWaiting.notifyAll();
        }
        synchronized (notAckedMsgsForAll){
            notAckedMsgsForAll.notifyAll();
        }
    }

    @Override
    public void turnOff() {
        disconnect();
        try {
            socket.close();
        } catch (Exception e){
            System.out.println(e.getLocalizedMessage());
        }
        stopListeningForGames();
        listeningThread.interrupt();
        if(pingAndResendTimer != null){
            pingAndResendTimer.cancel();
        }
        if(timeoutTimer != null){
            timeoutTimer.cancel();
        }
        stateSender.shutdown();
        toMasterSender.shutdown();
        state = NetworkState.NONE;
    }

}
