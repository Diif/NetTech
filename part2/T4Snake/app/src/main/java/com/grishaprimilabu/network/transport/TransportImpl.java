//package com.grishaprimilabu.network.transport;
//
//import com.google.protobuf.InvalidProtocolBufferException;
//import com.grishaprimilabu.model.entities.Config;
//import com.grishaprimilabu.network.NetworkConfig;
//import com.grishaprimilabu.proto.SnakesProto;
//import lombok.Getter;
//import lombok.Setter;
//
//import java.io.IOException;
//import java.net.*;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.PriorityQueue;
//import java.util.concurrent.ArrayBlockingQueue;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicLong;
//
//import static com.grishaprimilabu.network.NetworkConfig.*;
//public class TransportImpl implements TransportLayer {
//
//    private final InetAddress GROUP_ADDR;
//    private final MulticastSocket multicastSocket;
//    private final DatagramSocket datagramSocket;
//
//    private final AtomicLong ownSeq = new AtomicLong(0);
//
//    public TransportImpl() throws IOException {
//        multicastSocket = new MulticastSocket(PORT);
//        GROUP_ADDR = InetAddress.getByName(GROUP_ADDR_STR);
//        multicastSocket.joinGroup(GROUP_ADDR);
//        multicastSocket.setSoTimeout(MULTICAST_SOCKET_TIMEOUT);
//        datagramSocket = new DatagramSocket(0);
//        datagramSocket.setSoTimeout(UNICAST_SOCKET_TIMEOUT);
//
//        listeningThread = new Thread(() -> {
//            try {
//                listenerJob();
//            } catch (InterruptedException e){
//                System.out.println("Listening thread interrupted.");
//            }
//        });
//
//        sendingThread = new Thread(() -> {
//            try {
//                senderJob();
//            }catch (InterruptedException e){
//                System.out.println("Sender thread interrupted");
//            }
//        });
//
//        sendingThread.start();
//        listeningThread.start();
//
//
//    }
//
//    @Override
//    public void sendMulticast(SnakesProto.GameMessage.Builder msgBuilder) throws IOException {
//        byte[] data =  msgBuilder.setMsgSeq(ownSeq.getAndIncrement()).build().toByteArray();
//        DatagramPacket packet = new DatagramPacket(data, data.length);
//        packet.setAddress(GROUP_ADDR);
//        packet.setPort(PORT);
//        datagramSocket.send(packet);
//    }
//
//    @Override
//    public DatagramPacket receiveMulticast() throws IOException{
//        DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE_BYTES], MAX_PACKET_SIZE_BYTES);
//        multicastSocket.receive(packet);
//        byte[] msgData = Arrays.copyOf(packet.getData(), packet.getLength());
//        packet.setData(msgData);
//        return packet;
//    }
//
//
//    @Override
//    public DatagramPacket receiveUnicast() throws InterruptedException{
//        return msgToReceive.take();
//    }
//
//    @Override
//    public DatagramPacket receiveUnicastWithTimeoutInMillis(int timeoutMls) throws InterruptedException{
//        return msgToReceive.poll(timeoutMls, TimeUnit.MILLISECONDS);
//    }
//
//    @Override
//    public void sendUnicast(SnakesProto.GameMessage.Builder msgBuilder, SocketAddress address) throws IOException {
//        byte[] data = msgBuilder.setMsgSeq(ownSeq.getAndIncrement()).build().toByteArray();
//        DatagramPacket packet = new DatagramPacket(data, data.length);
//        packet.setSocketAddress(address);
//        msgToSend.add(packet);
//    }
//
//    @Override
//    public void addConnection(int playerId, SocketAddress address) {
//        connectedPlayers.put(address, playerId);
//    }
//
//    @Override
//        public void resetConnections(){
//        ownSeq.set(0);
//        nextSeqToReceive.clear();
//        notAckedMsg.clear();
//        msgToReceive.clear();
//        msgToSend.clear();
//        connectedPlayers.clear();
//        numResends.clear();
//    }
//    @Override
//    public void close() {
//     datagramSocket.close();
//     multicastSocket.close();
//     listeningThread.interrupt();
//     sendingThread.interrupt();
//    }
//
//    private void sendAck(SocketAddress address, int senderId, int receiverId, long seq){
//        byte[] data = SnakesProto.GameMessage.newBuilder().setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build())
//                .setMsgSeq(seq)
//                .setReceiverId(receiverId)
//                .setSenderId(senderId)
//                .build()
//                .toByteArray();
//        DatagramPacket packet = new DatagramPacket(data, data.length);
//        packet.setSocketAddress(address);
//        msgToSend.add(packet);
//    }
//
//    private final ArrayBlockingQueue<DatagramPacket> msgToSend = new ArrayBlockingQueue<>(QUEUE_SIZE);
//    private final ArrayBlockingQueue<DatagramPacket> msgToReceive = new ArrayBlockingQueue<>(QUEUE_SIZE);
//    private final ConcurrentHashMap<Long, DatagramPacket> notAckedMsg = new ConcurrentHashMap<>();
//    private final HashMap<DatagramPacket, Integer> numResends = new HashMap<>();
//    private final HashMap<SocketAddress, Long> nextSeqToReceive = new HashMap<>();
//    private final ConcurrentHashMap<SocketAddress, Integer> connectedPlayers = new ConcurrentHashMap<>();
//
//    @Setter
//    private Integer ownId = null;
//    private Thread listeningThread = null;
//    private Thread sendingThread = null;
//
//    private void senderJob() throws InterruptedException {
//        DatagramPacket packet = null;
//        while (true){
//
//            for (DatagramPacket packet1 : notAckedMsg.values()){
//                try {int numTries = numResends.get(packet1);
//                    if(numTries != MAX_RESENDS){
//                        datagramSocket.send(packet1);
//                        numResends.put(packet1, numTries+1);
//                    } else {
//                        numResends.remove(packet1);
//                        notAckedMsg.remove(SnakesProto.GameMessage.parseFrom(packet1.getData()).getMsgSeq());
//                        System.out.println("Can't receive ack for msg.");
//                    }
//                } catch (IOException e){
//                    System.out.println(e.getMessage());
//                }
//            }
//
//            while (!msgToSend.isEmpty() || packet != null){
//                try {
//                    if(packet == null){
//                        packet = msgToSend.take();
//                    }
//                    SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(packet.getData());
//                    if (!(message.hasAck() || message.hasDiscover() || message.hasError() || message.hasAnnouncement())){
//                        numResends.put(packet, 0);
//                        notAckedMsg.put(message.getMsgSeq(), packet);
//                    }
//                    datagramSocket.send(packet);
//                    packet = null;
//                } catch (IOException e){
//                    System.out.println(e.getMessage());
//                }
//            }
//            packet = msgToSend.poll(ACK_WAITING_TIME_MLS, TimeUnit.MILLISECONDS);
//        }
//    }
//
//    private void listenerJob() throws InterruptedException{
//        while (true){
//            DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE_BYTES], MAX_PACKET_SIZE_BYTES);
//            SnakesProto.GameMessage message = null;
//            try {
//                datagramSocket.receive(packet);
//                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
//
//                packet.setData(data);
//                message = SnakesProto.GameMessage.parseFrom(data);
//            }catch (SocketTimeoutException e){continue;}
//            catch (Exception e){
//                System.out.println(e.getMessage());
//                continue;
//            }
//            long msgSeq = message.getMsgSeq();
//
//            // join must be handled by network logic
//            if (message.hasJoin()) {
//                msgToReceive.add(packet);
//                continue;
//            }
//            // ack case
//            if(message.hasAck()){
//                if(notAckedMsg.containsKey(msgSeq)){
//                    notAckedMsg.remove(msgSeq);
//                    numResends.remove(notAckedMsg.get(msgSeq));
//                    msgToReceive.add(packet);
//                } else {
//                    continue;
//                }
//            }
//            //ignore if unknown and not join/ack
//            if(!connectedPlayers.containsKey(packet.getSocketAddress())){
//                continue;
//            }
//            // announcement and discovery case, must not ack
//            else if(message.hasAnnouncement() || message.hasDiscover()){
//              msgToReceive.add(packet);
//            }
//            // other must ack
//            else {
//                SocketAddress address = packet.getSocketAddress();
//                if(nextSeqToReceive.containsKey(address)){
//                    long expectedSeq = nextSeqToReceive.get(address);
//                    if(msgSeq < expectedSeq){
//                        sendAck(packet.getSocketAddress(), message.getReceiverId(), message.getSenderId(), msgSeq);
//                    }
//                    if(msgSeq == expectedSeq){
//                        sendAck(packet.getSocketAddress(), message.getReceiverId(), message.getSenderId(), msgSeq);
//                        msgToReceive.add(packet);
//                    }
//                    //msgSeq > expectedSeq
//                    //дропаем, ибо в протоколе про хендшейки ничего нет, а значит размер окон не узнать.
//                        continue;
//                } else {
//                    nextSeqToReceive.put(address, msgSeq + 1);
//                    sendAck(packet.getSocketAddress(), message.getReceiverId(), message.getSenderId(), msgSeq);
//                }
//            }
//
//            if(Thread.currentThread().isInterrupted()){
//                throw new InterruptedException();
//            }
//        }
//
//    }
//
//
//}
