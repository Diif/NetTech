package proxy.server;

import lombok.extern.java.Log;
import proxy.server.messages.ClientMethodsMsg;
import proxy.server.messages.ClientRequestMsg;
import proxy.server.messages.ServerMethodChoiceMsg;
import proxy.server.messages.SocksMsg;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

@Log
public class Proxy {

    private static class ClientInfo{
        SelectionKey clientKey;
        SelectionKey hostKey;
        InetSocketAddress hostAdr;
        STATE state;
        final Queue<ByteBuffer> msgToClient = new ArrayDeque<ByteBuffer>();
        final Queue<ByteBuffer> msgFromClient = new ArrayDeque<ByteBuffer>();
        int pendingTries = 0;
        static final int MAX_TRIES = 3;
        enum STATE{
            UNAUTHORIZED, WAITING_FOR_REQUEST, PENDING_CONNECTION, CONNECTED, REQUEST_FAILED
        }

    }
    private final int MAX_AUTHORIZE_SIZE = 1 + 1 + 255;
    private final int MIN_AUTHORIZE_SIZE = 3;
    private final int MAX_DOMAIN_NAME_SIZE = 253;
    private final int MIN_DOMAIN_NAME_SIZE = 3;
    private final int MAX_SOCKS_REQUEST_SIZE = 1 + 1 + 1 + 1 + MAX_DOMAIN_NAME_SIZE + 2;
    private final int MIN_SOCKS_REQUEST_SIZE = 1 + 1 + 1 + 1 + MIN_DOMAIN_NAME_SIZE + 2;
    private final int MAX_MSG_SIZE = 1024 * 512;
    private final ServerSocketChannel serverSocket;
    private final Selector selector;


    private Proxy(int port) throws IOException {
        selector = Selector.open();

        serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress("localhost", port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        log.info("Запуск прокси успешен, адрес: " + serverSocket.getLocalAddress());
    }

    private void StartSelection() throws IOException {
        while (true){
            selector.select();
            Set<SelectionKey> keySet = selector.selectedKeys();

            for (SelectionKey key : keySet){
                if(!key.isValid()){
                    continue;
                }
                if(key.isAcceptable()){
                    handleNewConnection();
                }

                if(!key.isValid()){
                    continue;
                }
                if(key.isConnectable()){
                    handleConnection(key);
                }

                if(!key.isValid()){
                    continue;
                }
                if(key.isReadable()){
                    handleRead(key);
                }

                if(!key.isValid()){
                    continue;
                }
                if(key.isWritable()){
                    handleWrite(key);
                }

            }
            keySet.clear();
        }
    }

    private void handleConnection(SelectionKey key){
        ClientInfo info = (ClientInfo) key.attachment();

        if(info.state == ClientInfo.STATE.PENDING_CONNECTION){
            InetSocketAddress hostAddr;
            try {
                if(!((SocketChannel) key.channel()).finishConnect()){
                    return;
                }
                hostAddr =(InetSocketAddress) ((SocketChannel) key.channel()).getRemoteAddress();
            } catch (IOException e){
                log.warning("Не удалось закончить установку соединения (закрываю соединение): " + e.getLocalizedMessage());
                sendRequestReply(SocksMsg.REPLY.HOST_UNREACHABLE,
                        true,
                        info.hostAdr.getAddress().getAddress(),
                        info.hostAdr.getPort(),
                        info);
                info.state = ClientInfo.STATE.REQUEST_FAILED;
                return;
            }
            sendRequestReply(SocksMsg.REPLY.SUCCEEDED,
                    true,
                    hostAddr.getAddress().getAddress(),
                    hostAddr.getPort(),
                    info);
            info.state = ClientInfo.STATE.CONNECTED;
            info.clientKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            info.hostKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            log.info("Подтверждение соединение отправлено: " + hostAddr.getAddress() +":"+ hostAddr.getPort());
        }


    }
    private void handleNewConnection(){
        SocketChannel clientChannel;
        try {
            log.info("Обработка входящего соединения...");
            clientChannel = serverSocket.accept();
        } catch (IOException e){
            log.info("Обработка входящего соединения не удалась: " + e.getLocalizedMessage());
            return;
        }

        ClientInfo info = new ClientInfo();
        try {
            clientChannel.configureBlocking(false);
            info.clientKey = clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, info);
            info.state = ClientInfo.STATE.UNAUTHORIZED;
            log.info("Клиент подключился с сокета: " + clientChannel.getRemoteAddress());
        } catch (IOException e){
                log.info("Закрываю новое соединение: " + e.getLocalizedMessage());
                cancelKeyAndCloseChannel(info.clientKey);
        }

    }

    private void handleRead(SelectionKey key){
        ClientInfo info = (ClientInfo) key.attachment();

        switch (info.state){
            case CONNECTED -> {
                try {
                    log.fine("Попытка приема данных...");
                    acceptData(key, info);
                } catch (IOException e){
                    log.info("не удалось принять данные (закрываю соединение): " + e.getLocalizedMessage());
                    cancelKeyAndCloseChannel(info.clientKey);
                    cancelKeyAndCloseChannel(info.hostKey);
                }
            }
            case WAITING_FOR_REQUEST -> {
                try {
                    log.info("Обработка запроса...");
                    acceptRequest(key);
                } catch (IOException e){
                    log.info("не удалось обработать запрос пользователя (закрываю соединение): " + e.getLocalizedMessage());
                    cancelKeyAndCloseChannel(key);
                }
            }
            case UNAUTHORIZED -> {
                try {
                    log.info("Авторизация...");
                    authorize(key);
                } catch (IOException e){
                    log.info("не удалось авторизовать пользователя (закрываю соединение): " + e.getLocalizedMessage());
                    cancelKeyAndCloseChannel(key);
                }
            }
            default -> {}
        }
    }

    private void acceptData(SelectionKey key, ClientInfo info) throws IOException{
        ByteBuffer msg = ByteBuffer.allocate(MAX_MSG_SIZE);
        int numBytes = ((SocketChannel) key.channel()).read(msg);
        if(numBytes < 0){
            log.warning("Количество прочитанных байт данных оказалось невалидным (закрываю соединение): " + numBytes);
            cancelKeyAndCloseChannel(info.clientKey);
            cancelKeyAndCloseChannel(info.hostKey);
            return;
        }
        msg.flip();
        if (key == info.clientKey){
            log.info("Клиент передает данные хосту");
            info.msgFromClient.add(msg);
            info.hostKey.interestOpsOr(SelectionKey.OP_WRITE);
        } else {
            log.info("Хост передает данные клиенту");
            info.clientKey.interestOpsOr(SelectionKey.OP_WRITE);
            info.msgToClient.add(msg);
        }
    }

    public void handleWrite(SelectionKey key){
        ClientInfo info = (ClientInfo) key.attachment();

        switch (info.state){
            case CONNECTED -> {
                if (key == info.clientKey){
                    log.fine("Попытка передачи данных клиенту...");
                    sendToClient(key, info);
                } else if (key == info.hostKey){
                    log.fine("Попытка передачи данных от клиента...");
                    sendFromClient(key,info);
                } else {
                    log.severe("Обнаружен хрен пойми какой ключ");
                }
            }
            case WAITING_FOR_REQUEST, UNAUTHORIZED -> {
                if(key == info.clientKey){
                    log.info("Передача авторизации/запроса клиенту...");
                    sendToClient(key,info);
                } else {
                    log.warning("При авторизации/запросе данные запросил не клиент");
                }
            }
            case REQUEST_FAILED -> {
                if(null != info.hostAdr){
                    log.info("Закрытие канала после неудачного запроса на адрес: " + info.hostAdr.getAddress() + ":" + info.hostAdr.getPort());
                } else{
                    log.info("Закрытие канала после неудачного запроса. Адрес не поддерживается.");
                }
                sendToClient(key, info);
                cancelKeyAndCloseChannel(info.clientKey);
                cancelKeyAndCloseChannel(info.hostKey);
            }
            default -> {}
        }


    }

    private final ByteBuffer requestBuffer = ByteBuffer.allocate(MAX_SOCKS_REQUEST_SIZE);
    private void acceptRequest(SelectionKey key) throws IOException{
        requestBuffer.clear();

        ClientInfo info =(ClientInfo) key.attachment();
        int numBytes = ((SocketChannel) key.channel()).read(requestBuffer);
        if (numBytes < MIN_SOCKS_REQUEST_SIZE){
            log.warning("Количество прочитанных байт запроса оказалось невалидным (закрываю соединение): " + numBytes);
            sendRequestReply(
                    SocksMsg.REPLY.GENERAL_ERROR,
                    true,
                    new byte[4],
                    0,
                    info);
            info.state = ClientInfo.STATE.REQUEST_FAILED;
            return;
        }

        SocksMsg msg = SocksMsg.parseFrom(SocksMsg.MSG_CLIENT_TYPE.CLIENT_REQUEST, requestBuffer);
        if(msg.isInvalid()){
            throw new IOException("был получен невалидный запрос");
        }

        ClientRequestMsg clientRequestMsg = msg.getClientRequestMsg();
        if(clientRequestMsg.hasConnectCmd()){
            if(clientRequestMsg.hasIpv4()){
                InetAddress address;
                try {
                    address = InetAddress.getByAddress(clientRequestMsg.getIp());
                } catch (UnknownHostException e){
                    log.info("При запросе о подключении не удалось получить адрес: " + e.getLocalizedMessage());
                    sendRequestReply(
                            SocksMsg.REPLY.HOST_UNREACHABLE,
                            true,
                            clientRequestMsg.getIp(),
                            clientRequestMsg.getPort(),
                            info);
                    info.state = ClientInfo.STATE.REQUEST_FAILED;
                    return;
                }
                if(openConnection(address, clientRequestMsg.getPort(),info)){
                    info.hostAdr = new InetSocketAddress(address, clientRequestMsg.getPort());
                    info.state = ClientInfo.STATE.PENDING_CONNECTION;
                } else {
                    log.info("Не удалось открыть соединение");
                    sendRequestReply(SocksMsg.REPLY.HOST_UNREACHABLE,
                            true,
                            clientRequestMsg.getIp(),
                            clientRequestMsg.getPort(),
                            info);
                    info.state = ClientInfo.STATE.REQUEST_FAILED;
                }
            }else if(clientRequestMsg.hasDomain()){
                //todo
                log.info("Нет поддержки доменных имен");
                sendRequestReply(SocksMsg.REPLY.ADDRESS_NOT_SUPPORTED,
                        false,
                        clientRequestMsg.getDomain(),
                        clientRequestMsg.getPort(),
                        info);
                info.state = ClientInfo.STATE.REQUEST_FAILED;
            } else {
                log.info("Нет поддержки доменных IPv6");
                sendRequestReply(SocksMsg.REPLY.ADDRESS_NOT_SUPPORTED,
                        false,
                        clientRequestMsg.getIp(),
                        clientRequestMsg.getPort(),
                        info);
                info.state = ClientInfo.STATE.REQUEST_FAILED;
            }
        } else {
            log.info("Нет поддержки команд.");
            if(clientRequestMsg.hasDomain()){
                sendRequestReply(SocksMsg.REPLY.COMMAND_NOT_SUPPORTED,
                        false,
                        clientRequestMsg.getDomain(),
                        clientRequestMsg.getPort(),
                        info);
            } else{
                sendRequestReply(SocksMsg.REPLY.COMMAND_NOT_SUPPORTED,
                        false,
                        clientRequestMsg.getIp(),
                        clientRequestMsg.getPort(),
                        info);
            }
            info.state = ClientInfo.STATE.REQUEST_FAILED;
        }


    }

    private void cancelKeyAndCloseChannel(SelectionKey key){
        if(null == key){
            return;
        }
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException e){
            log.info("При попытке закрыть канал произошла ошибка: " + e.getLocalizedMessage());
        }
    }
    private boolean openConnection(InetAddress address, int port, ClientInfo info){
        SocketChannel socketChannel;
        try {
            socketChannel = SocketChannel.open();
        } catch (IOException e){
            log.info("Не удалось открыть соединение: " + e.getLocalizedMessage());
            return false;
        }

        try {
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(address, port));
        } catch (IOException e){
            log.info("Не удалось законнектить сокет: " + e.getLocalizedMessage());
            try {
                socketChannel.close();
            } catch (IOException ignore){}
            return false;
        }


        try {
            info.hostKey = socketChannel.register(selector, SelectionKey.OP_CONNECT, info);
            info.clientKey.interestOps(0);
        } catch (IOException e){
            log.info("Не удалось зарегистрировать канал: " + e.getLocalizedMessage());
            try {
                socketChannel.close();
            } catch (IOException ignore){}
            return false;
        }
        try {
            log.info("Начат процесс соединения до: " + ((SocketChannel) info.hostKey.channel()).getRemoteAddress().toString());
        } catch (IOException e){
            log.info("не удалось вывести новое соединение: " + e.getLocalizedMessage());
        }
        return true;

    }

    private void sendRequestReply(SocksMsg.REPLY reply, boolean isIpv4, byte[] addr, int port, ClientInfo info){
        info.msgToClient.add(SocksMsg.createServerReply(reply,isIpv4, addr,port).toByteBuffer());
    }

    private final ByteBuffer authorizeBuffer = ByteBuffer.allocate(MAX_AUTHORIZE_SIZE);

    private void authorize(SelectionKey key) throws IOException{
        authorizeBuffer.clear();

        int numBytes = ((SocketChannel) key.channel()).read(authorizeBuffer);
        if (numBytes < MIN_AUTHORIZE_SIZE){
            log.warning("Количество прочитанных байт авторизации оказалось невалидным (закрываю соединение): " + numBytes);
            ClientInfo info = (ClientInfo) key.attachment();
            cancelKeyAndCloseChannel(info.clientKey);
            cancelKeyAndCloseChannel(info.hostKey);
            return;
        }

        SocksMsg msg = SocksMsg.parseFrom(SocksMsg.MSG_CLIENT_TYPE.CLIENT_METHODS, authorizeBuffer);
        if(msg.isInvalid()){
            throw new IOException("при авторизации было получено невалидное сообщение");
        }

        ClientMethodsMsg clientMethodsMsg = msg.getClientMethodsMsg();

        if (clientMethodsMsg.hasNoAuth()){
            sendAuthMethod(SocksMsg.AUTH_METHODS.NO_AUTH, (ClientInfo) key.attachment());
        } else {
            sendAuthMethod(SocksMsg.AUTH_METHODS.NOT_SUPPORTED, (ClientInfo) key.attachment());
        }


    }

    private void sendAuthMethod(SocksMsg.AUTH_METHODS method, ClientInfo info){
        ServerMethodChoiceMsg msg = SocksMsg.createServerMethodChoice(method);

        info.msgToClient.add(msg.toByteBuffer());
        info.state = ClientInfo.STATE.WAITING_FOR_REQUEST;
    }

    private void sendToClient(SelectionKey key, ClientInfo info){
        Queue<ByteBuffer> queue = info.msgToClient;
        if(queue.isEmpty()){
//            if(info.clientKey.isWritable() && null != info.hostKey && info.hostKey)
            key.interestOps(SelectionKey.OP_READ);
            return;
        }
        SocketChannel channel = (SocketChannel) key.channel();
        while (!queue.isEmpty()){
            try {
                log.info("Отправка данных клиенту!");
                send(channel, queue.poll());
            } catch (IOException e){
                log.info("Не удалось отправить сообщение клиенту (закрываю соединение): "+ e.getLocalizedMessage());
                cancelKeyAndCloseChannel(info.clientKey);
                cancelKeyAndCloseChannel(info.hostKey);
            }
        }
    }
    private void sendFromClient(SelectionKey key, ClientInfo info){
        Queue<ByteBuffer> queue = info.msgFromClient;
        if(queue.isEmpty()){
            key.interestOps(SelectionKey.OP_READ);
            return;
        }
        SocketChannel channel = (SocketChannel) key.channel();

        while (!queue.isEmpty()){
            try {
                log.info("Отправка данных от клиента!");
                send(channel, queue.poll());
            } catch (IOException e){
                log.info("Не удалось отправить сообщение от клиента (закрываю соединение): "+ e.getLocalizedMessage());
                cancelKeyAndCloseChannel(info.clientKey);
                cancelKeyAndCloseChannel(info.hostKey);
            }
        }
    }
    private void send(SocketChannel channel, ByteBuffer data) throws IOException{
        channel.write(data);
    }

    public static void startProxy(int port) throws IOException{
        Proxy proxy = new Proxy(port);

        proxy.StartSelection();
    }
}
