package T1MulticastApp;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Map;

public class View extends Application {
    private static TextArea userTextArea;
    private static Controller controller;

    private static InetAddress groupAddress;

    public void GUIStart(Controller controller, InetAddress groupAddress){
        View.controller = controller;
        View.groupAddress = groupAddress;
        launch();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Гриша прими лабу");
        primaryStage.setResizable(false);

        View.userTextArea = new TextArea();
        View.userTextArea.setEditable(false);
        View.userTextArea.setMaxWidth(500);
        View.userTextArea.setMinHeight(700);

        Scene scene = new Scene(View.userTextArea);

        primaryStage.setScene(scene);

        controller.setGroup(groupAddress);

        updateView();
        primaryStage.show();
    }
    @Override
    public void stop(){
            controller.leaveGroup();
    }
    public void updateView(){
        StringBuilder builder = new StringBuilder();

        for(Map.Entry<String, HashSet<String>> x : Model.users().entrySet()) {
            String ip = x.getKey();
            HashSet<String> names = x.getValue();
            for (String name : names) {
                builder.append(ip);
                builder.append("  ");
                builder.append(name);
                builder.append('\n');
            }
        }

        userTextArea.setText(builder.toString());

    }

}
