package T1MulticastApp;

import javafx.application.Platform;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Arrays;
import java.util.HexFormat;

public class Controller {
    private final MulticastSocket socket;
    private Thread listeningThread;
    private InetAddress groupAddress;
    private final DatagramPacket sendPacket;
    private final DatagramPacket recPacket;
    private View view;
    boolean isAsking = false;

    public Controller() throws IOException {
        socket = new MulticastSocket(Model.APP_PORT);
        recPacket = new DatagramPacket(new byte[Model.APP_JOIN_KEY.length() + 1 + Model.APP_USER_MAX_NAME + 1 + Model.UUID_LEN + 1],
                Model.APP_JOIN_KEY.length()+ 1 + Model.APP_USER_MAX_NAME + 1 + Model.UUID_LEN + 1);
        sendPacket = new DatagramPacket(new byte[Model.APP_JOIN_KEY.length() + 1 + Model.APP_USER_MAX_NAME + 1 + + Model.UUID_LEN + 1],
                Model.APP_JOIN_KEY.length()+ 1 + Model.APP_USER_MAX_NAME + 1 + + Model.UUID_LEN + 1);
        groupAddress = null;
    }

    public void startApp(String userName, InetAddress groupAddress, Model.IP_TYPE ipType){
        Model.setUserName(userName);
        Model.setIpType(ipType);
        this.view = new View();
        sendPacket.setPort(Model.APP_PORT);
        sendPacket.setAddress(groupAddress);
        recPacket.setPort(Model.APP_PORT);
        recPacket.setAddress(groupAddress);
        view.GUIStart(this,groupAddress);
    }

    public void setGroup(InetAddress address){
        if(null != groupAddress){
            return;
        }

        try {
            socket.joinGroup(address);
            groupAddress = address;
            listeningThread = createListeningThread();
            listeningThread.start();
            sendJoinDatagram();
        } catch (IOException e){
            System.err.println("Can't join group with address: " + Arrays.toString(groupAddress.getAddress()));
        }
    }

    public void leaveGroup(){
        if(null != groupAddress){
            try {
                listeningThread.interrupt();
                sendExitDatagram();
                socket.leaveGroup(groupAddress);
                socket.close();
            } catch (Exception e){
                System.err.println("Can't leave group with address: " + Arrays.toString(groupAddress.getAddress()) + "\n" + e.getLocalizedMessage());
            }
        }
    }

    private Thread createListeningThread(){
        return new Thread(() -> {
            System.out.println("Thread starts.");
            while (true) {
                try {
                    socket.receive(recPacket);
                    String data = new String(recPacket.getData());
                    Arrays.fill(recPacket.getData(), (byte) 0);
                    System.out.println(data);
                    String userName, ip = null, id;
                    Model.DATAGRAM_TYPE packetType;
                    try {
                        packetType = checkAndGetDatagramType(data);
                        userName = getNameFromDatagramData(data);
                        id = getIdFromDatagramData(data);
                        switch (Model.getIpType()) {
                            case IPV4 -> ip = convertByteArrayToIpv4String(recPacket.getAddress().getAddress());
                            case IPV6 -> ip = convertByteArrayToIpv6String(recPacket.getAddress().getAddress());
                        }
                        switch (packetType){
                            case JOIN -> {
                                Model.addUser(ip, id,userName);
                                sendRepeatDatagram();
                                System.out.println("Join packet. Add user with IP: " + ip + " and name + " + userName);
                            }
                            case EXIT -> {
                                Model.removeUser(ip, id);
                                System.out.println("Exit packet. Remove user with IP: " + ip + " and name: " + userName);
                            } case REPEAT -> Model.addUser(ip,id, userName);
                        }
                    } catch (RuntimeException e){
                        System.out.println(e.getLocalizedMessage());
                    } catch (Exception e){
                        return;
                    }
                    try {
                        Platform.runLater(view::updateView);
                    } catch (Exception e){
                        continue;
                    }
                } catch (Exception e){
                    return;
                }
            }
        });
    }

    private Model.DATAGRAM_TYPE checkAndGetDatagramType(String data){
        if (data.matches("^"+ Model.APP_JOIN_KEY + ".*")){
            return Model.DATAGRAM_TYPE.JOIN;
        } if (data.matches("^"+ Model.APP_EXIT_KEY + ".*")){
            return Model.DATAGRAM_TYPE.EXIT;
        } if (data.matches("^"+ Model.APP_REPEAT_KEY + ".*")) {
            return Model.DATAGRAM_TYPE.REPEAT;
        }
        throw new RuntimeException("Invalid datagram.");
    }
    private String getNameFromDatagramData(String data){
        return data.substring(data.indexOf(' ')+1, data.lastIndexOf(' '));
    }

    private String getIdFromDatagramData(String data){return data.substring(data.lastIndexOf(' ')+1);}

    private void sendExitDatagram() throws IOException {
        sendPacket.setData((Model.APP_EXIT_KEY + " " + Model.getUserName() + " " + Model.ID + "\0").getBytes());
        socket.send(sendPacket);
    }

    private void sendJoinDatagram() throws IOException {
        sendPacket.setData((Model.APP_JOIN_KEY + " " + Model.getUserName() + " " + Model.ID + "\0").getBytes());
        socket.send(sendPacket);
    }

    private void sendRepeatDatagram() throws IOException {
        sendPacket.setData((Model.APP_REPEAT_KEY + " " + Model.getUserName() + " " + Model.ID + "\0").getBytes());
        socket.send(sendPacket);
    }

    private String convertByteArrayToIpv4String(byte[] ipBytes){
        int[] ipInt = new int[ipBytes.length];
        for(int i = 0; i < ipBytes.length; ipInt[i] = ipBytes[i++] & 0xff);
        return Arrays
                .stream(ipInt)
                .mapToObj(String::valueOf)
                .reduce((a, b) -> a.concat(".").concat(b))
                .get();
    }

    private String convertByteArrayToIpv6String(byte[] array){
        return HexFormat.of().formatHex(array);
    }

}
