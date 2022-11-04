import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.ApiConsts;
import network.entity.Location;
import view.Gui;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class App {

    public static void main(String[] args) {
        ApiConsts.loadKeys();
        Gui gui = new Gui();
        gui.startGui();
    }

}
