package com.grishaprimilabu.view.javafxgui;

import com.grishaprimilabu.model.field.FieldTitle;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class TitleLabel extends Label {
    private final FieldTitle title;
    private final ImageView EMPTY_IMAGEVIEW;
    private final ImageView SNAKE_IMAGEVIEW;
    private final ImageView FOOD_IMAGEVIEW;
    private final int TITLE_SIZE = 25;
    private FieldTitle.Type curType = FieldTitle.Type.EMPTY;

    private static final Image EMPTY_IMAGE;
    private static final Image SNAKE_IMAGE;
    private static final Image FOOD_IMAGE;

    static {
        FileInputStream input = null;
        try {
            input = new FileInputStream(Utils.EMPTY_IMAGE_PATH);
        } catch (FileNotFoundException e) {
            System.out.println("ГРИША ЗАПУСКАЙ ПО ИНСТРУКЦИИ");
            throw new RuntimeException(e);
        }
        EMPTY_IMAGE = new Image(input);

        try {
            input = new FileInputStream(Utils.SNAKE_IMAGE_PATH);
        } catch (FileNotFoundException e) {
            System.out.println("ГРИША ЗАПУСКАЙ ПО ИНСТРУКЦИИ");
            throw new RuntimeException(e);
        }
        SNAKE_IMAGE = new Image(input);

        try {
            input = new FileInputStream(Utils.FOOD_IMAGE_PATH);
        } catch (FileNotFoundException e) {
            System.out.println("ГРИША ЗАПУСКАЙ ПО ИНСТРУКЦИИ");
            throw new RuntimeException(e);
        }
        FOOD_IMAGE = new Image(input);
    }
    public TitleLabel(FieldTitle title){
        super();

        EMPTY_IMAGEVIEW = new ImageView(EMPTY_IMAGE);
        EMPTY_IMAGEVIEW.setFitHeight(TITLE_SIZE);
        EMPTY_IMAGEVIEW.setFitWidth(TITLE_SIZE);


        SNAKE_IMAGEVIEW = new ImageView(SNAKE_IMAGE);
        SNAKE_IMAGEVIEW.setFitHeight(TITLE_SIZE);
        SNAKE_IMAGEVIEW.setFitWidth(TITLE_SIZE);


        FOOD_IMAGEVIEW = new ImageView(FOOD_IMAGE);
        FOOD_IMAGEVIEW.setFitHeight(TITLE_SIZE);
        FOOD_IMAGEVIEW.setFitWidth(TITLE_SIZE);

        this.setGraphic(EMPTY_IMAGEVIEW);
        this.title = title;
    }

    public void update(){
        FieldTitle.Type type = title.getTitleType();
        if(type == curType){
            return;
        }
        switch (type){
            case EMPTY -> {
                this.setGraphic(EMPTY_IMAGEVIEW);
                curType = FieldTitle.Type.EMPTY;
            }
            case SNAKE -> {
                this.setGraphic(SNAKE_IMAGEVIEW);
                curType = FieldTitle.Type.SNAKE;
            }
            case FOOD -> {
                this.setGraphic(FOOD_IMAGEVIEW);
                curType = FieldTitle.Type.FOOD;
            }
        }
    }

}
