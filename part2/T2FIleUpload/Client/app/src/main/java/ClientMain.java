import lombok.extern.java.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

@Log
public class ClientMain {

    public static void main(String[] args) throws InterruptedException {
        int port;
        try {
            port = Integer.parseInt(args[1]);
        }catch (Exception e){
            log.warning(Thread.currentThread().getName() + " Client: can't parse port. Default is assigned: 11112.");
            port = 11112;
        }

        InetAddress address;
        if(isIpv4(args[0]) || isIpv6(args[0])){
            try {
                address = getAddressByString(args[0]);
            }catch (Exception e){
                log.severe(Thread.currentThread().getName() + " Client: can't parse address.");
                return;
            }
        } else {
            try {
                address = InetAddress.getByName(args[0]);
            } catch (Exception e){
                log.severe(Thread.currentThread().getName() + " Client: unknown hostname.");
                return;
            }
        }


        try {
            Client client = new Client(address, port);
            try {
                client.sendFile(args[2]);
            } catch (Exception e){
                log.severe("Can't send file to server :(  .\n\t" + e.getLocalizedMessage());
            }
        } catch (Exception e) {
            log.severe("Can't connect to server :(  .\n\t" + e.getLocalizedMessage());
        }

    }

    private static InetAddress getAddressByString(String ipMaybe) throws UnknownHostException {
        if(isIpv4(ipMaybe))
        {
            String[] ipParts = ipMaybe.split("\\.");
            byte[] ip = new byte[4];
            for(int i = 0, maxParts = 4; i < maxParts; i++ ){
                ip[i] =((byte)Integer.parseInt(ipParts[i]));
            }
            return InetAddress.getByAddress(ip);
        } else if(isIpv6(ipMaybe))
        {
            String[] ipParts = ipMaybe.split(":");
            String hexIpString = String.join("",ipParts);
            HexFormat hex = HexFormat.of().withLowerCase();
            byte[] ipv6InBytes = hex.parseHex(hexIpString);
            return InetAddress.getByAddress(ipv6InBytes);
        } else
        {
            throw new RuntimeException("Incorrect passed ip.\n");
        }
    }

    private static boolean isIpv4(String ipMaybe){
        return ipMaybe.indexOf('.') != -1;
    }

    private static boolean isIpv6(String ipMaybe){
        return ipMaybe.indexOf(':') != -1;
    }

}
