package T1MulticastApp;

import java.util.HashSet;
import java.util.HashMap;
import java.util.UUID;

public class Model {
    public static final String APP_JOIN_KEY = "ff4f367ddeacfd7036b74f4c37263dca024fa9b0cd442630a8226d486ceb4dea";
    public static final String APP_EXIT_KEY = "51ed371e5f888423176399f920c4e14a3316495edd2bdcf268012e73e19eaa2d";
    public static final String APP_REPEAT_KEY = "4067928e08870ca16e95770b98541dfdffefbae9f25eac467d1e5f8279d6f627";

    enum DATAGRAM_TYPE {
        JOIN, EXIT, REPEAT;
    }

    enum IP_TYPE{
        IPV4, IPV6
    }
    public static final int APP_PORT = 11113;
    public static final int APP_USER_MAX_NAME = 10;
    public static final int UUID_LEN = 36;

    public static final UUID ID = UUID.randomUUID();

    public static IP_TYPE getIpType() {
        return ipType;
    }

    public static void setIpType(IP_TYPE ipType) {
        Model.ipType = ipType;
    }

    private static IP_TYPE ipType;

    public static String getUserName() {
        return userName;
    }

    public static void setUserName(String userName) {
        Model.userName = userName;
    }

    private static String userName;

    private final static HashMap<String, HashMap<String, String>> users = new HashMap<>();

    static public void addUser(String ip, String id, String name){
        HashMap<String, String> existedMap = users.get(ip);
        if(null != existedMap){
            existedMap.put(id,name);
        } else{
            HashMap<String, String> newMap = new HashMap<>();
            newMap.put(id, name);
            users.put(ip, newMap);
        }
    }

    static public HashMap<String,HashMap<String, String>> users(){
        return users;
    }

    static public void removeUser(String ip, String id){
        HashMap<String, String> map = users.get(ip);
        if(null == map){
            return;
        }
        map.remove(id);
        if(map.isEmpty()){
            users.remove(ip);
        }
    }


}
