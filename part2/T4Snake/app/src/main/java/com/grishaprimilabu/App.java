package com.grishaprimilabu;

import com.grishaprimilabu.controller.ControllerForViewImpl;
import com.grishaprimilabu.model.GameModel;
import com.grishaprimilabu.model.entities.Config;
import com.grishaprimilabu.network.NetworkLogic;
import com.grishaprimilabu.proto.SnakesProto;
import com.grishaprimilabu.view.javafxgui.JavaFXView;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class App {

public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {

    ControllerForViewImpl controller = new ControllerForViewImpl();
    NetworkLogic networkLogic = new NetworkLogic();
    JavaFXView view = new JavaFXView();
    GameModel model = new GameModel(view, networkLogic, Config.getInstance());
    networkLogic.init(model);
    controller.init(model, networkLogic);

    Thread thread = new Thread( () ->view.startGui(controller));
    thread.start();

    try {
        Thread.sleep(250);
    }catch (Exception e){}

    view.recreateField(model.getField());
    model.applyConfig(SnakesProto.GameConfig.newBuilder().setHeight(25).setWidth(25).setFoodStatic(1).setStateDelayMs(300).build());

//    int i = 0;
//    double res =0;
//    CompletableFuture.supplyAsync( () -> {
//        double sadfa = 0;
//        for (int j = 0; j < 1000000000; j++){
//            sadfa = Math.sqrt(j);
//        }
//        return sadfa;
//    }).thenAccept( (rs) -> supermetod(rs, rs));
//
//    for (i = 0; i < 1000000; i++){
//        res = Math.sqrt(i);
//    }
//    System.out.println(Thread.currentThread().getName() + res + i);
//////    Thread.sleep(1000);
//    }
////
//    private static void supermetod(double st, double stt){
//        System.out.println(Thread.currentThread().getName() + st + "tsed");
    }

}