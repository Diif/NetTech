package proxy.server;

import lombok.extern.java.Log;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;
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
import java.nio.charset.StandardCharsets;
import java.util.*;

@Log
public class Proxy {

    private static class ClientInfo{
        SelectionKey clientKey;
        SelectionKey hostKey;
        byte[] hostIp;
        int hostPort;
        STATE state;
        final Queue<ByteBuffer> msgToClient = new ArrayDeque<ByteBuffer>();
        final Queue<ByteBuffer> msgFromClient = new ArrayDeque<ByteBuffer>();
        int pendingTries = 0;
        static final int MAX_TRIES = 3;
        enum STATE{
            UNAUTHORIZED, WAITING_FOR_REQUEST, PENDING_CONNECTION, RESOLVING_DOMAIN, CONNECTED, REQUEST_FAILED
        }
    }

    private static class DnsInfo{
        final Queue<ByteBuffer> msgToResolver = new ArrayDeque<ByteBuffer>();
        private final HashMap<String, HashSet<ClientInfo>> waitingClients = new HashMap<>();
        private final InetSocketAddress resolverAddr;

        private final DatagramChannel dnsChannel;

        DnsInfo(InetSocketAddress address, DatagramChannel dnsChannel){
            this.resolverAddr = address;
            this.dnsChannel = dnsChannel;
        }
    }
    private final int MAX_AUTHORIZE_SIZE = 1 + 1 + 255;
    private final int MIN_AUTHORIZE_SIZE = 3;
    private final int MAX_DOMAIN_NAME_SIZE = 253;
    private final int MIN_DOMAIN_NAME_SIZE = 3;
    private final int MAX_SOCKS_REQUEST_SIZE = 1 + 1 + 1 + 1 + MAX_DOMAIN_NAME_SIZE + 2;
    private final int MIN_SOCKS_REQUEST_SIZE = 1 + 1 + 1 + 1 + MIN_DOMAIN_NAME_SIZE + 2;
    private final int MAX_MSG_SIZE = 1024 * 512;
    private final int MAX_DNS_RESPONSE_SIZE = 512;
    private final String DNS_ADDR = "8.8.8.8";
    private final int DNS_PORT = 53;
    private final ServerSocketChannel serverSocket;
    private final Selector selector;

    private final SelectionKey dnsKey;

    private Proxy(int port) throws IOException {
        selector = Selector.open();

        serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress("localhost", port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        // гугловский днс является рекурсивным так что норм
        DatagramChannel dnsChannel = DatagramChannel.open();
        dnsChannel.bind(null);
        DnsInfo dnsInfo = new DnsInfo(new InetSocketAddress(InetAddress.getByName(DNS_ADDR), DNS_PORT), dnsChannel);
        dnsChannel.connect(dnsInfo.resolverAddr);
        dnsChannel.configureBlocking(false);
        dnsKey = dnsChannel.register(selector, SelectionKey.OP_READ, dnsInfo);

        log.info("Запуск прокси закончен, адрес: " + serverSocket.getLocalAddress());
    }

    private void StartSelection() throws IOException {
        while (true){
            selector.select();
            Set<SelectionKey> keySet = selector.selectedKeys();

            for (SelectionKey key : keySet){
                if(!key.isValid()){
                    continue;
                }

                if (key == dnsKey){
                    handleDnsKey();
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

    private void handleDnsKey(){
        if(dnsKey.isReadable()){
            try {
                readDnsResponse();
            } catch (IOException e){
                log.warning("Не удалось прочитать ответ резолвера (закрываю все ожидающие соединения): " + e.getLocalizedMessage());
                DnsInfo dnsInfo = (DnsInfo) dnsKey.attachment();
                for(Map.Entry<String, HashSet<ClientInfo>> domainWithClients : dnsInfo.waitingClients.entrySet()){
                    byte[] domain = domainWithClients.getKey().getBytes(StandardCharsets.UTF_8);
                    HashSet<ClientInfo> clients = domainWithClients.getValue();
                    for (ClientInfo info : clients){
                        sendRequestReply(
                                SocksMsg.REPLY.HOST_UNREACHABLE,
                                false,
                                domain,
                                0,
                                info);
                        info.state = ClientInfo.STATE.REQUEST_FAILED;
                        info.clientKey.interestOps(SelectionKey.OP_WRITE);
                    }
                    clients.clear();
                }
            }
        }

        if(dnsKey.isWritable()){
            try {
                sendDnsQuery();
            }catch (IOException e){
                log.warning("Не удалось отправить сообщение резолверу (закрываю все ожидающие соединения): " + e.getLocalizedMessage());
                DnsInfo dnsInfo = (DnsInfo) dnsKey.attachment();
                for(Map.Entry<String, HashSet<ClientInfo>> domainWithClients : dnsInfo.waitingClients.entrySet()){
                    byte[] domain = domainWithClients.getKey().getBytes(StandardCharsets.UTF_8);
                    HashSet<ClientInfo> clients = domainWithClients.getValue();
                    for (ClientInfo info : clients){
                        sendRequestReply(
                                SocksMsg.REPLY.GENERAL_ERROR,
                                false,
                                domain,
                                0,
                                info);
                        info.state = ClientInfo.STATE.REQUEST_FAILED;
                        info.clientKey.interestOps(SelectionKey.OP_WRITE);
                    }
                    clients.clear();
                }
            }
        }
    }

    private void readDnsResponse() throws IOException {
        DnsInfo dnsInfo =  (DnsInfo) dnsKey.attachment();
        ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_DNS_RESPONSE_SIZE);
        if(0 == dnsInfo.dnsChannel.read(byteBuffer)){
            return;
        }

        Message msg = new Message(byteBuffer.flip());
        String domain = msg.getQuestion().getName().toString();
        log.info("Пришел ответ от резолвера на домен:" + domain);
        HashSet<ClientInfo> clients = dnsInfo.waitingClients.get(domain);
        if (null == clients) {
            domain = msg.getQuestion().getName().toString(true);
            clients = dnsInfo.waitingClients.get(domain);
        }
        if(clients == null || clients.isEmpty()){
            log.warning("Пришел ответ от резолвера на ненужый домен: " + domain);
            return;
        }

        List<Record> records = msg.getSection(1);
        if (msg.getRcode() != 0 || records.isEmpty()){
            log.info("Пришел плохой ответ от резолвера (закрываю все ожидающие соединения).");
            byte[] dmn = domain.getBytes(StandardCharsets.UTF_8);
            for (ClientInfo info : clients){
                sendRequestReply(
                        SocksMsg.REPLY.HOST_UNREACHABLE,
                        false,
                        dmn,
                        0,
                        info);
                info.state = ClientInfo.STATE.REQUEST_FAILED;
                info.clientKey.interestOps(SelectionKey.OP_WRITE);
            }
            clients.clear();
            return;
        }

        Record record = records.get(0);
        byte[] address = InetAddress.getByName(record.rdataToString()).getAddress();
        for (ClientInfo clientInfo : clients){
            log.info("Соединение по домену...");
            connectToIpv4(address, clientInfo.hostPort, clientInfo);
        }
        clients.clear();
    }

    private void sendDnsQuery() throws IOException {
        DnsInfo info =(DnsInfo) dnsKey.attachment();

        for(int i = 0, max = info.msgToResolver.size(); i < max; i++){
            ByteBuffer msg = info.msgToResolver.poll();
            int count = info.dnsChannel.send(msg, info.resolverAddr);
            if(count <= 0){
                log.warning("Не удалось отправить сообщение на резолвер.");
                info.msgToResolver.add(msg);
            }
        }
        if(info.msgToResolver.isEmpty()){
            dnsKey.interestOps(SelectionKey.OP_READ);
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
                        info.hostIp,
                        info.hostPort,
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
            info.hostKey.interestOps(SelectionKey.OP_READ);
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
            info.clientKey = clientChannel.register(selector, SelectionKey.OP_READ, info);
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
            log.fine("Клиент передает данные хосту");
            info.msgFromClient.add(msg);
            info.hostKey.interestOpsOr(SelectionKey.OP_WRITE);
        } else {
            log.fine("Хост передает данные клиенту");
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
                if(null != info.hostIp){
                    try {
                        log.info("Закрытие канала после неудачного запроса на адрес: " + InetAddress.getByAddress(info.hostIp));
                    } catch (UnknownHostException e){
                        log.info("Закрытие канала после неудачного запроса на адрес.");
                    }
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
        key.interestOps(SelectionKey.OP_WRITE);
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
                connectToIpv4(clientRequestMsg.getIp(), clientRequestMsg.getPort(), info);
            }else if(clientRequestMsg.hasDomain()){
                connectToDomain(clientRequestMsg, info);
            } else {
                connectToIpv6(clientRequestMsg, info);
            }

        } else {
            unsupportedCommands(clientRequestMsg, info);
        }


    }

    private void unsupportedCommands(ClientRequestMsg clientRequestMsg, ClientInfo info){
        log.info("Нет поддержки команды для запроса.");
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

    private void connectToDomain(ClientRequestMsg clientRequestMsg, ClientInfo info){
        log.info("Обработка запроса на коннект по домену");
        if (resolveDomainName(new String(clientRequestMsg.getDomain(), StandardCharsets.UTF_8), info)){
            info.hostPort = clientRequestMsg.getPort();
            info.state = ClientInfo.STATE.RESOLVING_DOMAIN;
        } else {
            sendRequestReply(SocksMsg.REPLY.HOST_UNREACHABLE,
                    false,
                    clientRequestMsg.getDomain(),
                    clientRequestMsg.getPort(),
                    info);
            info.state = ClientInfo.STATE.REQUEST_FAILED;
        }
    }

    private void connectToIpv6(ClientRequestMsg clientRequestMsg, ClientInfo info){
        log.info("Нет поддержки IPv6 для запроса");
        sendRequestReply(SocksMsg.REPLY.ADDRESS_NOT_SUPPORTED,
                false,
                clientRequestMsg.getIp(),
                clientRequestMsg.getPort(),
                info);
        info.state = ClientInfo.STATE.REQUEST_FAILED;
    }
    private void connectToIpv4(byte[] ip, int port, ClientInfo info){
        InetAddress address;
        try {
            address = InetAddress.getByAddress(ip);
        } catch (UnknownHostException e){
            log.info("При запросе о подключении не удалось получить адрес: " + e.getLocalizedMessage());
            sendRequestReply(
                    SocksMsg.REPLY.HOST_UNREACHABLE,
                    true,
                    ip,
                    port,
                    info);
            info.state = ClientInfo.STATE.REQUEST_FAILED;
            info.clientKey.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        if(openConnection(address, port,info)){
            info.hostIp = ip;
            info.hostPort = port;
            info.state = ClientInfo.STATE.PENDING_CONNECTION;
        } else {
            log.info("При запросе не удалось открыть соединение");
            sendRequestReply(SocksMsg.REPLY.HOST_UNREACHABLE,
                    true,
                    ip,
                    port,
                    info);
            info.state = ClientInfo.STATE.REQUEST_FAILED;
            info.clientKey.interestOps(SelectionKey.OP_WRITE);
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

    private boolean resolveDomainName(String domain, ClientInfo clientInfo){
        Record queryRecord;
        DnsInfo dnsInfo =(DnsInfo) dnsKey.attachment();
        String absDom = domain;
        try {
            absDom = domain + ".";
            queryRecord = Record.newRecord(Name.fromString(absDom), Type.A,DClass.IN);
            HashSet<ClientInfo> clients = dnsInfo.waitingClients.get(absDom);
            if(null == clients){
                clients = new HashSet<>();
                dnsInfo.waitingClients.put(absDom, clients);
            }
            clients.add(clientInfo);
        } catch (TextParseException e){
            try {
                absDom = domain;
                queryRecord = Record.newRecord(Name.fromString(absDom), Type.A,DClass.IN);
                HashSet<ClientInfo> clients = dnsInfo.waitingClients.get(absDom);
                if(null == clients){
                    clients = new HashSet<>();
                }
                clients.add(clientInfo);
            } catch (TextParseException e1){
                log.warning("Не удалось распарсить доменное имя: " + e1.getLocalizedMessage());
                return false;
            }
        }

        dnsInfo.msgToResolver.add(ByteBuffer.wrap(Message.newQuery(queryRecord).toWire()).asReadOnlyBuffer());
        dnsKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        clientInfo.clientKey.interestOps(0);
        log.info("Запрос на резолвинг был поставлен в очередь: " + authorizeBuffer);
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
        info.clientKey.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        info.state = ClientInfo.STATE.WAITING_FOR_REQUEST;
    }

    private void sendToClient(SelectionKey clientKey, ClientInfo info){
        Queue<ByteBuffer> queue = info.msgToClient;
        SocketChannel channel = (SocketChannel) clientKey.channel();

        log.fine("Отправка данных клиенту!");
        sendAll(channel, info, queue, clientKey);
    }
    private void sendFromClient(SelectionKey hostKey, ClientInfo info){
        Queue<ByteBuffer> queue = info.msgFromClient;
        SocketChannel channel = (SocketChannel) hostKey.channel();

        log.fine("Отправка данных от клиента!");
        sendAll(channel, info, queue, hostKey);
    }
    private void sendAll(SocketChannel channel, ClientInfo info, Queue<ByteBuffer> queue, SelectionKey key){
        for (int i =0, max = queue.size(); i < max; i++){
            try {
                ByteBuffer msg = queue.poll();
                if(channel.write(msg) == 0){
                    queue.add(msg);
                }
            } catch (IOException e){
                log.info("Не удалось отправить сообщение (закрываю соединение): "+ e.getLocalizedMessage());
                cancelKeyAndCloseChannel(info.clientKey);
                cancelKeyAndCloseChannel(info.hostKey);
                return;
            }
        }
        if(queue.isEmpty()){
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    public static void startProxy(int port) throws IOException{
        Proxy proxy = new Proxy(port);

        proxy.StartSelection();
    }
}
