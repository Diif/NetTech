package proxy;

import lombok.extern.java.Log;
import proxy.server.Proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

@Log
public class App {
    public static void main(String[] args) {
        int port;
        try {
            port = getPort(args);
        } catch (Exception e){
            System.out.println(e.getLocalizedMessage());
            return;
        }

        try {
            Proxy.startProxy(port);
        } catch (IOException e){
            System.out.println(e.getLocalizedMessage());
        }
//
//        Selector selector;
//        try {
//            selector = Selector.open();
//        }catch (IOException e){
//            log.severe(e.getLocalizedMessage());
//            return;
//        }
//
//        SocketChannel socketChannel;
//        try {
//            socketChannel = SocketChannel.open();
//        } catch (IOException e){
//            log.info("Не удалось открыть соединение: " + e.getLocalizedMessage());
//            return;
//        }
//
//        try {
//            socketChannel.configureBlocking(false);
//            socketChannel.connect(new InetSocketAddress("34.120.208.123", 443));
//        } catch (IOException e){
//            log.info("Не удалось законнектить сокет: " + e.getLocalizedMessage());
//            try {
//                socketChannel.close();
//                return;
//            } catch (IOException ignore){}
//        }
//
//
//        try {
//            socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT);
//        } catch (IOException e){
//            log.info("Не удалось зарегистрировать канал: " + e.getLocalizedMessage());
//            try {
//                socketChannel.close();
//            } catch (IOException ignore){}
//        }
//
//
//        int i = 1;
//        while (true){
//            log.info("################TRY " + i++ + "##########");
//            try {
//                selector.select();
//            } catch (IOException e){
//                log.severe("Strange");
//                return;
//            }
//            Set<SelectionKey> keySet = selector.selectedKeys();
//            for(SelectionKey key : keySet){
//                log.info("Interests: " + key.isWritable() + " and write " + key.isReadable() + " and conn " + key.isConnectable());
//                SocketChannel chan = (SocketChannel) key.channel();
//                if(chan.isConnectionPending()){
//                    log.info("CONN PEND");
//                }
//                if (chan.isConnected()){
//                    log.info("CONN");
//                }
//                if(chan.isOpen()){
//                    log.info("OPEN");
//                }
//                try {
//                    if(chan.finishConnect()){
//                        log.info("SUCCEED");
//                        socketChannel.close();
//                        key.cancel();
//                        return;
//                    }
//                } catch (IOException e){
//                    log.severe("FAIL");
//                }
//            }
//        }



    }

    private static int getPort(String[] args) throws Exception{
        if (args.length < 1){
            throw new Exception("not enough args");
        }

        return Integer.parseInt(args[0]);

    }

}
