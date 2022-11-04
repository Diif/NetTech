package view;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.java.Log;
import network.ApiHelper;
import network.entity.Location;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

@Log
public class Gui extends Application {


    public void startGui(){
        launch();
    }

    @Override
    public void start(Stage primaryStage) {
        SplitPane mainPane = new SplitPane();

        PlaceDetailsSection placeDetailsSection = new PlaceDetailsSection();

        mainPane.getItems().addAll(new SearchSection(placeDetailsSection),placeDetailsSection);
        Scene scene = new Scene(mainPane);
        primaryStage.setScene(scene);
        primaryStage.setWidth(600);
        primaryStage.setHeight(600);
        primaryStage.show();
    }


}
