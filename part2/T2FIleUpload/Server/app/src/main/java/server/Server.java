package server;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import protocol.ProtocolServerHelper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log
public class Server {
    private final ServerSocket socket;
    private final ExecutorService threadPool;
    private final ScheduledExecutorService scheduledThreadPool;
    private final SpeedCounter speedCounter;


    public Server(int port) throws IOException {
        socket = new ServerSocket(port);
        threadPool = Executors.newCachedThreadPool();
        speedCounter = new SpeedCounter();
        scheduledThreadPool = Executors.newScheduledThreadPool(1);
    }

    public void startListen(){
        log.fine(Thread.currentThread().getName() + " Server: listening...");
        scheduledThreadPool.scheduleAtFixedRate(speedCounter::PrintAndUpdateSpeedInfo,3,3, TimeUnit.SECONDS);
        while (!socket.isClosed()){
            try {
                Socket connection = socket.accept();
                threadPool.submit(() -> processClientRequest(connection));
                log.fine(Thread.currentThread().getName() + " Server: accepted new connection.");
            } catch (Exception e){
                log.info(Thread.currentThread().getName() + " Server: NOOOO my socket collection:\n\t" + e.getLocalizedMessage());
            }
        }
        log.fine(Thread.currentThread().getName() + " Server: closed.");
    }

    @SneakyThrows
    private void processClientRequest(Socket clientSocket){
        ProtocolServerHelper helper = null;
        try {
            try {
                helper = new ProtocolServerHelper(clientSocket);
            } catch (Exception e){
                clientSocket.close();
                log.severe(Thread.currentThread().getName() + " Server: can't create ProtocolHelper for client:\n\t" + e.getLocalizedMessage());
                return;
            }
            speedCounter.addClient(helper);
            helper.downloadFile();
        } catch (Exception e){
            speedCounter.removeClient(helper);
            log.severe(Thread.currentThread().getName() + " Server: can't process client request:\n\t" + e.getLocalizedMessage());
        }
        helper.close();


    }

}
