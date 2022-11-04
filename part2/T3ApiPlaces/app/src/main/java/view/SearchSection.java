package view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.java.Log;
import network.ApiHelper;
import network.entity.Location;

import java.beans.PropertyChangeEvent;

import java.io.FileInputStream;
import java.util.List;

@Log
public class SearchSection extends VBox {
    private final Button searchButton;
    private final TextField searchField;
    private final ListView<Location> locationListView;

    private final PlaceDetailsSection rightPart;


    public SearchSection(PlaceDetailsSection rightPart){
        super();

        this.rightPart = rightPart;

        HBox searchPart = new HBox();
        searchButton = createSearchButton();
        searchField = createSearchField();
        locationListView = createLocationListView();

        searchPart.getChildren().addAll(searchField,searchButton);
        this.getChildren().addAll(searchPart, locationListView);
    }

    private TextField createSearchField(){
        TextField searchField = new TextField("Write location name here.");
        searchField.setPrefHeight(32);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        return searchField;
    }

    private Button createSearchButton(){
        Button searchButton;
        try {
            FileInputStream imageFile = new FileInputStream("src/main/resources/search.png");
            Image image = new Image(imageFile);
            ImageView imageView = new ImageView(image);
            searchButton = new Button("", imageView);
        } catch (Exception e){
            log.warning("GUI: can't create search button with image: " + e.getLocalizedMessage());
            searchButton = new Button("GO");
        }

        searchButton.setOnAction(actionEvent -> {
            try {
                ApiHelper.getLocationsByName(searchField.getText()).thenAccept(this::setLocations);
            } catch (Exception e){
                log.warning("Controller: can't get locations." + e.getLocalizedMessage());
            }
        });

        return searchButton;
    }

    private ListView<Location> createLocationListView(){
        ListView<Location> locationListView = new ListView<>();
        locationListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        locationListView.getSelectionModel().selectedItemProperty().addListener((obs, oldval,newval) -> {
            rightPart.updatePlaces(newval);
        });

        VBox.setVgrow(locationListView, Priority.ALWAYS);

        return locationListView;
    }

    private void setLocations(List<Location> locations){
        if(locationListView == null ){
            log.warning("GUI: attempt to set locations with null ListVIew ");
            return;
        }
        ObservableList<Location> observableList = FXCollections.observableArrayList(locations);
        Platform.runLater(() -> locationListView.setItems(observableList));

        for (Location location : observableList){
            log.info(location.toString());
        }

    }


}
