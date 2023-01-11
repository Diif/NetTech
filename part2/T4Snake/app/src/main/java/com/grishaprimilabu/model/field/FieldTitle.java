package com.grishaprimilabu.model.field;

import lombok.Getter;

@Getter
public class FieldTitle {
    public enum Type {EMPTY, FOOD, SNAKE}

    private Type titleType;
    private int playerId;

    public FieldTitle(){
        titleType = Type.EMPTY;
        playerId = -1;
    }

    public void makeEmpty(){
        this.playerId = -1;
        this.titleType = Type.EMPTY;
    }

    public boolean isFood(){
        return titleType == Type.FOOD;
    }

    public boolean isEmpty(){
        return titleType == Type.EMPTY;
    }

    public boolean isSnake(){
        return titleType == Type.SNAKE;
    }


    public FieldTitle makeFood(){
        this.playerId = -1;
        this.titleType = Type.FOOD;
        return this;
    }

    public FieldTitle makeSnake(int playerId){
        this.titleType = Type.SNAKE;
        this.playerId = playerId;
        return this;
    }

}
