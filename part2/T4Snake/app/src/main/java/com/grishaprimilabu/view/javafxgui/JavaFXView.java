package com.grishaprimilabu.view.javafxgui;

import com.grishaprimilabu.controller.ControllerForView;
import com.grishaprimilabu.model.PlayersInfo;
import com.grishaprimilabu.model.entities.Config;
import com.grishaprimilabu.model.field.Field;
import com.grishaprimilabu.proto.SnakesProto;
import com.grishaprimilabu.view.View;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;

public class JavaFXView extends Application implements View {

    private static ControllerForView controllerForView;

    private static GridPane field;
    private static VBox menu;
    private static VBox inGameMenu;
    private static HBox layout;
    private static Stage primaryStage;
    private static VBox players;

    private static final Font bigFont = Font.loadFont("file:src/main/resources/superfont.woff", 42);
    private static final Font smallFont = Font.loadFont("file:src/main/resources/font2.ttf", 16);
    public void startGui(ControllerForView controllerForView){
        JavaFXView.controllerForView = controllerForView;
        launch();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Умоляю Гриша прими лабу");
        JavaFXView.primaryStage = primaryStage;
        primaryStage.setOnCloseRequest((event -> controllerForView.turnOffApp()));

        layout = new HBox();

        initMenu();
        initField();
        initInGameMenu();

        layout.getChildren().addAll(field, menu);

        HBox.setHgrow(menu, Priority.ALWAYS);
        HBox.setHgrow(field, Priority.ALWAYS);

        Scene scene = new Scene(layout);
        layout.setBackground(new Background(new BackgroundFill(new Color(0.607, 0.462, 0.325,1), CornerRadii.EMPTY, Insets.EMPTY)));
        addSceneKeyListener(scene);
        scene.getStylesheets().addAll(Path.of("src/main/resources/buttonsStyle.css").toUri().toURL().toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initMenu(){
        Button startButton = createStartButton();
        Button settingsButton = createSettingsButton();
        Button findGameButton = createFindGameButton();
        Button exitButton = createExitButton();

        styleButton(startButton, bigFont);
        styleButton(settingsButton, bigFont);
        styleButton(findGameButton, bigFont);
        styleButton(exitButton, bigFont);

        menu = new VBox(startButton, findGameButton, settingsButton, exitButton);
        menu.setBackground(new Background(new BackgroundFill(new Color(0.607, 0.462, 0.325,1), CornerRadii.EMPTY, Insets.EMPTY)));
    }

    private void initInGameMenu(){
        Button disconnectButton = createDisconnectButton();
        Button changeRoleButton = createChangeRoleButton();

        styleButton(disconnectButton, bigFont);
        styleButton(changeRoleButton, bigFont);


        players = new VBox();
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(players);
        VBox.setVgrow(players, Priority.ALWAYS);

        inGameMenu = new VBox(scrollPane, changeRoleButton, disconnectButton);
        scrollPane.setMinHeight(disconnectButton.getHeight() * 2);
        scrollPane.setMinWidth(disconnectButton.getWidth() * 2);

    }

    private void initField(){
        if (JavaFXView.field == null){
            JavaFXView.field = new GridPane();
            JavaFXView.field.setAlignment(Pos.CENTER);
        }
    }

    private Button createStartButton(){
        Button button = new Button("Start");
        button.setOnAction((event) -> {
            layout.getChildren().set(1, inGameMenu);
            controllerForView.startServerModeAndPlay();
        });
        return button;
    }

    private Button createSettingsButton(){
        Button button = new Button("Settings");
        button.setOnAction((event) -> {
            Stage stage = new Stage();

            ArrayList<Label> labels = new ArrayList<>();
            ArrayList<TextField> fields = new ArrayList<>();

            Label width = createLabelForSettings("Width");
            labels.add(width);
            Label height = createLabelForSettings("Height");
            labels.add(height);
            Label minFood = createLabelForSettings("Min food");
            labels.add(minFood);
            Label timeDelay = createLabelForSettings("Time delay");
            labels.add(timeDelay);
            Label playerName = createLabelForSettings("Player name");
            labels.add(playerName);
            Label gameName = createLabelForSettings("Game name");
            labels.add(gameName);
            Label role = createLabelForSettings("Role");
            labels.add(role);

            TextField widthTF = new TextField(Config.getInstance().getWidth().toString());
            fields.add(widthTF);
            TextField heightTF = new TextField(Config.getInstance().getHeight().toString());
            fields.add(heightTF);
            TextField minFoodTF = new TextField(Config.getInstance().getFoodStatic().toString());
            fields.add(minFoodTF);
            TextField timeDelayTF = new TextField(Config.getInstance().getStateDelayMs().toString());
            fields.add(timeDelayTF);
            TextField playerNameTF = new TextField(Config.getInstance().getPlayerName());
            fields.add(playerNameTF);
            TextField gameNameTF = new TextField(Config.getInstance().getGameName());
            fields.add(gameNameTF);

            ComboBox<SnakesProto.NodeRole> roleComboBox = new ComboBox<>();
            roleComboBox.getItems().add(SnakesProto.NodeRole.NORMAL);
            roleComboBox.getItems().add(SnakesProto.NodeRole.VIEWER);
            roleComboBox.getSelectionModel().selectFirst();

            GridPane gridPane = new GridPane();
            gridPane.addColumn(0, width,height,minFood,timeDelay,playerName,gameName,role);
            gridPane.addColumn(1,widthTF,heightTF,minFoodTF, timeDelayTF,playerNameTF,gameNameTF,roleComboBox);
            gridPane.setPadding(new Insets(5,5,5,5));

            for (Label label : labels){
                setGridPaneAllGrow(label);
            }

            for(TextField field1 : fields){
                setGridPaneAllGrow(field1);
            }
            setGridPaneAllGrow(roleComboBox);

            Button saveButton = new Button("Save");
            styleButton(saveButton, bigFont);
            saveButton.setOnAction(event1 -> {
                Config config = Config.getInstance();

                config.setGameName(gameNameTF.getText());
                config.setPlayerName(playerNameTF.getText());
                try {
                    controllerForView.updateConfig(SnakesProto.GameConfig.newBuilder()
                            .setWidth(Integer.parseInt(widthTF.getText()))
                            .setHeight(Integer.parseInt(heightTF.getText()))
                            .setFoodStatic(Integer.parseInt(minFoodTF.getText()))
                            .setStateDelayMs(Integer.parseInt(timeDelayTF.getText()))
                            .build());
                    config.setRole(roleComboBox.getValue());
                    stage.close();
                } catch (Exception e){
                    System.out.println("Блин блинский, в настройках плохие циферки...");
                }
            });
            Button backButton = createBackButton(stage);

            styleButton(saveButton, bigFont);
            styleButton(backButton, bigFont);
            VBox layout = new VBox(gridPane, saveButton,backButton);

            Scene scene = new Scene(layout);
            try {
                scene.getStylesheets().addAll(Path.of("src/main/resources/buttonsStyle.css").toUri().toURL().toExternalForm());
            } catch (Exception e){
                System.out.println("ГРИША ЧИТАЙ ИНСТРУКЦИЮ");
            }
            stage.setScene(scene);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(JavaFXView.layout.getScene().getWindow());
            stage.show();

        });
        return button;
    }

    private Button createBackButton(Stage stage){
        Button button = new Button("Back");
        styleButton(button, bigFont);
        button.setOnAction(event -> {
            stage.close();
            controllerForView.stopListeningForGames();
        });
        return button;
    }

    private Button createJoinButton(Stage stage, ListView<String> games){
        Button button = new Button("Join");
        styleButton(button, bigFont);
        button.setOnAction(event -> {
            String gameName = games.getSelectionModel().getSelectedItem();
            SnakesProto.GameMessage message = controllerForView.connectToGame(gameName);
            if (null == message){
                Alert alert = new Alert(Alert.AlertType.ERROR,"Не удалось подключиться к серверу.");
                alert.showAndWait();
            }
            else if(message.hasAck()){
                controllerForView.stopListeningForGames();
                stage.close();
                showInGameMenu();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR,message.getError().getErrorMessage());
                alert.showAndWait();
            }
        });
        return button;
    }

    private void setGridPaneAllGrow(Node node){
        GridPane.setVgrow(node, Priority.ALWAYS);
        GridPane.setHgrow(node, Priority.ALWAYS);
    }
    private Label createLabelForSettings(String text){
        Label label = new Label(text);
        label.setFont(smallFont);

        return label;

    }

    private Button createFindGameButton(){
        Button button = new Button("Find game");
        button.setOnAction((event) -> {
            Stage stage = new Stage();

            TextField ipTextField = new TextField("ip");
            TextField portTextField = new TextField("port");
            Button askServerButton = createAskServerButton(ipTextField, portTextField);
            HBox toServerSection = new HBox(ipTextField, portTextField, askServerButton);
            VBox.setVgrow(ipTextField, Priority.ALWAYS);
            VBox.setVgrow(portTextField, Priority.ALWAYS);
            HBox.setHgrow(ipTextField, Priority.ALWAYS);
            HBox.setHgrow(portTextField, Priority.ALWAYS);
            ipTextField.setMaxHeight(Double.MAX_VALUE);
            portTextField.setMaxHeight(Double.MAX_VALUE);

            ObservableList<String> observableList = FXCollections.observableList(new ArrayList<>());
            ListView<String> aliveGames = new ListView<>();
            aliveGames.setItems(observableList);

            aliveGames.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);


            Button backButton = createBackButton(stage);
            controllerForView.startListeningForGames(observableList);
            Button joinButton = createJoinButton(stage, aliveGames);

            VBox layout = new VBox(toServerSection,aliveGames, joinButton, backButton);

            Scene scene = new Scene(layout);
            try {
                scene.getStylesheets().addAll(Path.of("src/main/resources/buttonsStyle.css").toUri().toURL().toExternalForm());
            } catch (Exception e){
                System.out.println("ГРИША ЧИТАЙ ИНСТРУКЦИЮ");
            }
            stage.setScene(scene);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(JavaFXView.layout.getScene().getWindow());
            stage.show();
        });
        return button;
    }

    private Button createAskServerButton(TextField ipField, TextField portField){
        Button button = new Button("Ask");
        styleButton(button, bigFont);
        button.setOnAction((event) -> {
            try {
                controllerForView.askServerForGames(ipField.getText(), Integer.parseInt(portField.getText()));
            }catch (Exception e){
                System.out.println(e.getLocalizedMessage());
            }
        });
        return button;
    }
    private Button createExitButton(){
        Button button = new Button("Exit");
        button.setOnAction((event) -> {
            controllerForView.turnOffApp();
            primaryStage.close();
        });
        return button;
    }

    private Button createDisconnectButton(){
        Button button = new Button("Disconnect");
        button.setOnAction((event) -> {
            controllerForView.disconnect();
            showMainMenu();
            primaryStage.sizeToScene();
        });
        button.setPrefHeight(50);
        button.setMaxHeight(50);

        button.resize(50,100);
        return button;
    }

    private Button createChangeRoleButton(){
        Button button = new Button("Change role");
        button.setOnAction((event) -> {
            controllerForView.changeRoleToView();
        });
        return button;
    }

    private void styleButton(Button button, Font font){
        button.setId("button");
        if(font != null){
            button.setFont(font);
        }
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        VBox.setVgrow(button, Priority.ALWAYS);
    }

    private void addSceneKeyListener(Scene scene){
        scene.setOnKeyPressed( (KeyEvent e) -> {
            switch (e.getCode()){
                case A -> controllerForView.moveLeft();
                case D -> controllerForView.moveRight();
                case W -> controllerForView.moveUp();
                case S -> controllerForView.moveDown();
            }
        });
    }

    @Override
    public void updateView(SnakesProto.GamePlayers gamePlayers) {
        Platform.runLater(() -> {
            for(Node node : JavaFXView.field.getChildren()){
                TitleLabel titleLabel = (TitleLabel) node;
                titleLabel.update();
            }
            ObservableList<Node> observableList = players.getChildren();
            observableList.clear();
            for (SnakesProto.GamePlayer player : gamePlayers.getPlayersList()){
                Label label = new Label(player.getName() +" " + player.getScore());
                label.setFont(smallFont);
                observableList.add(label);
            }
        });
    }

    @Override
    public void recreateField(Field field) {
        int sizeX = field.getSizeX();
        int sizeY = field.getSizeY();

        Platform.runLater(() ->
        {
            JavaFXView.field.getChildren().clear();
            for (int x = 0; x < sizeX; x++){
                for (int y = 0; y < sizeY; y++){
                    TitleLabel test = new TitleLabel(field.getTitle(x,y));
                    JavaFXView.field.add(test,x,y);
                }
            }
            primaryStage.sizeToScene();
        });
    }

    private void showMainMenu(){
        layout.getChildren().set(1, menu);
    }

    private void showInGameMenu(){
        layout.getChildren().set(1, inGameMenu);
    }

}
