package view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.java.Log;
import network.ApiHelper;
import network.entity.InterestingPlace;
import network.entity.InterestingPlaceDetailed;
import network.entity.Location;
import network.entity.Weather;
import java.util.ArrayList;

@Log
public class PlaceDetailsSection extends VBox {

    private final TextArea weatherArea;
    private final ListView<InterestingPlace> placesListView;

    public PlaceDetailsSection(){
        super();
        weatherArea = new TextArea("Weather: rain\n" + "Temp: 20 C");
        weatherArea.setEditable(false);
        weatherArea.setMaxHeight(60);
        placesListView = new ListView<>();
        placesListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        placesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldval,newval) -> {
            ApiHelper.getInterestingPlaceDetails(newval).thenAccept(this::showDetails);
        });
        VBox.setVgrow(placesListView, Priority.ALWAYS);
        getChildren().addAll(weatherArea, placesListView);
    }

    public void updatePlaces(Location location){
        ApiHelper.getLocationWeather(location).thenAccept(this::setWeather);
        ApiHelper.getLocationInterestingPlaces(location).thenAccept(this::setInterestingPlaces);
    }

    private void setWeather(Weather weather){
        Platform.runLater(() -> {
            weatherArea.clear();
            weatherArea.setText("Weather: " + weather.getDescription() + "\nTemp: " + weather.getTemp() +" C" );
        });
    }

    private void setInterestingPlaces(ArrayList<InterestingPlace> places){
        ObservableList<InterestingPlace> observableList = FXCollections.observableArrayList(places);
        Platform.runLater(() -> placesListView.setItems(observableList));
        for(InterestingPlace place : places){
            log.info(place.toString());
        }
    }

    private void showDetails(InterestingPlaceDetailed place){

        Platform.runLater(() -> {

            Stage stage = new Stage();
            stage.setTitle("Place details");

            TextArea textArea = new TextArea();
            textArea.setEditable(false);
            textArea.setText(place.toString());

            stage.setScene(new Scene(textArea));
            stage.show();
        });

    }



}
