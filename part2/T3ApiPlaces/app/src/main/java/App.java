import network.ApiConsts;
import view.Gui;


public class App {

    public static void main(String[] args) {
        ApiConsts.loadKeys();
        Gui gui = new Gui();
        gui.startGui();
    }

}
