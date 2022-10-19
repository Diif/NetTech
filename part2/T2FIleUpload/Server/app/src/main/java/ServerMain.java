import lombok.extern.java.Log;
import server.Server;

import java.io.IOException;

@Log
public class ServerMain {

    public static void main(String[] args) {
        int port;
        try {
            port = Integer.parseInt(args[0]);
        }catch (Exception e){
            log.warning(Thread.currentThread().getName() + " Server: can't parse port. Default is assigned: 11112.");
            port = 11112;
        }
        try {
            Server server = new Server(port);
            (new Thread(server::startListen)).start();
        } catch (Exception e ){
            log.severe("Can't host server :(  .\n\t" + e.getLocalizedMessage());
        }

    }
}
