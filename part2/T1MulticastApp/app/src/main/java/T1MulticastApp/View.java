package T1MulticastApp;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.util.Map;

public class View extends Application {
    private TextArea userTextArea;

    public void GUIStart(){
        launch();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Гриша прими лабу");
        primaryStage.setResizable(false);

        userTextArea = new TextArea();
        userTextArea.setEditable(false);
        userTextArea.setMaxWidth(500);
        userTextArea.setMinHeight(700);

        Scene scene = new Scene(userTextArea);

        primaryStage.setScene(scene);
        updateView();
        primaryStage.show();
    }
// TODO поток крыть надо после закрытия GUI
    public void updateView(){
        StringBuilder builder = new StringBuilder();

        for(Map.Entry<String, String> x : Model.users().entrySet()) {
            builder.append(x.getKey());
            builder.append("  ");
            builder.append(x.getValue());
            builder.append('\n');
        }

        try {
            userTextArea.setText(builder.toString());
        } catch (NullPointerException e){
            //it's ok.
        }
    }

}
