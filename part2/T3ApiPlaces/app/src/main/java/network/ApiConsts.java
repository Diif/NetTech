package network;

import lombok.extern.java.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

@Log
public class ApiConsts {
    public static  String OPENTRIP_KEY = "test";
    public static  String GRAPHHOPPER_KEY = "test";
    public static  String OPENWEATHER_KEY = "test";

    public static final String INTERESTING_PLACES_RADIUS = "3000";

    static public void loadKeys(){
        File config = new File("src/main/java/keys.txt");
        try {
            Scanner scanner = new Scanner(config);
            OPENTRIP_KEY = scanner.nextLine();
            GRAPHHOPPER_KEY = scanner.nextLine();
            OPENWEATHER_KEY = scanner.nextLine();
        } catch (FileNotFoundException e){
            log.warning("App can't load keys for API: " + e.getLocalizedMessage() + "\nPath: " + config.getAbsolutePath());
        }
    }

}
