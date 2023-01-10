package com.grishaprimilabu.model.entities;

import com.grishaprimilabu.network.NetworkConfig;
import com.grishaprimilabu.proto.SnakesProto;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Config {
    private Integer width = 40;           // Ширина поля в клетках (от 10 до 100)
    private Integer height = 30;          // Высота поля в клетках (от 10 до 100)
    private Integer foodStatic  = 1;       // Количество клеток с едой, независимо от числа игроков (от 0 до 100)

    private Integer stateDelayMs = 1000; // Задержка между ходами (сменой состояний) в игре, в миллисекундах (от 100 до 3000)

    @Setter
    private String playerName = "Cool player name bro";
    @Setter
    private String gameName = "Cool game name bro";

    @Setter
    private SnakesProto.NodeRole role = SnakesProto.NodeRole.NORMAL;

    @Getter(AccessLevel.NONE)
    private static final Config instance = new Config();

    private SnakesProto.GameConfig gameConfigMsg;

    private Config(){
        gameConfigMsg =
                SnakesProto.GameConfig.newBuilder()
                        .setHeight(height)
                        .setWidth(width)
                        .setFoodStatic(foodStatic)
                        .setStateDelayMs(stateDelayMs)
                .build();
    }

    public static Config getInstance() {
        return instance;
    }


    public   void setWidth(Integer width) {
        if(width <= 100 && width >= 10) {
              this.width = width;
              this.gameConfigMsg = SnakesProto.GameConfig.newBuilder(gameConfigMsg).setWidth(width).build();
        }
    }


    public   void setHeight(Integer height) {
        if(height <= 100 && height >= 10) {
            this.height = height;
            this.gameConfigMsg = SnakesProto.GameConfig.newBuilder(gameConfigMsg).setHeight(height).build();
        }
    }


    public   void setFoodStatic (Integer foodStatic ) {
        if(foodStatic  >= 0 && foodStatic  <=100) {
            this.foodStatic  = foodStatic ;
            this.gameConfigMsg = SnakesProto.GameConfig.newBuilder(gameConfigMsg).setFoodStatic(foodStatic).build();
        }
    }

    public   void setStateDelayMs(Integer stateDelayMs) {
        if(stateDelayMs >= 100 && stateDelayMs <=3000) {
            this.stateDelayMs = stateDelayMs;
            this.gameConfigMsg = SnakesProto.GameConfig.newBuilder(gameConfigMsg).setStateDelayMs(stateDelayMs).build();

            NetworkConfig.PING_TIME_MLS = stateDelayMs / 10;
            NetworkConfig.TIMEOUT_TIME_MLS = stateDelayMs * 8 / 10;
        }
    }

}
