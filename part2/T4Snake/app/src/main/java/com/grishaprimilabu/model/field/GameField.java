package com.grishaprimilabu.model.field;

import com.grishaprimilabu.proto.SnakesProto;

import java.util.ArrayList;

public class GameField implements Field{

    private final FieldTitle[] field;
    private final int sizeX;
    private final int sizeY;
    private final  ArrayList<FieldTitle> dirtyTitles;

    public GameField(int sizeX, int sizeY){
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        field = new FieldTitle[sizeX * sizeY];
        for (int i = 0; i < sizeX * sizeY; i ++){
            field[i] = new FieldTitle();
        }
        dirtyTitles = new ArrayList<>();
    }

    public void updateField(SnakesProto.GameStateOrBuilder gameState){
        cleanDirtyTitles();

        for (SnakesProto.GameState.Coord foodCoord : gameState.getFoodsList()){
            dirtyTitles.add(getTitle(foodCoord.getX(), foodCoord.getY()).makeFood());
        }

        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()){
            putSnakeOnField(snake);
        }

    }

    public void cleanField(){
        dirtyTitles.clear();
        for(FieldTitle title : field){
            title.makeEmpty();
        }
    }
    private void putSnakeOnField(SnakesProto.GameState.Snake snake){
        boolean isHead = true;
        int prevX = 0;
        int prevY = 0;
        int playerId = snake.getPlayerId();
        for (SnakesProto.GameState.Coord pointCoord : snake.getPointsList()){
            if(isHead){
                isHead = false;
                prevX = pointCoord.getX();
                prevY = pointCoord.getY();
            } else {
                prevX += pointCoord.getX();
                prevY += pointCoord.getY();
            }
            dirtyTitles.add(getTitle(prevX,prevY).makeSnake(playerId));
        }
    }

    @Override
    public FieldTitle getTitle(int x, int y){
        if(x < 0 || y < 0 || x >= sizeX || y >= sizeY){
            throw new RuntimeException("Invalid coords: x:" + x + ", y:" + y);
        }

        return field[y*sizeX+ x];
    }

    @Override
    public FieldTitle getTitle(SnakesProto.GameState.Coord coord){
        int x = coord.getX();
        int y = coord.getY();
        if(x < 0 || y < 0 || x >= sizeX || y >= sizeY){
            throw new RuntimeException("Invalid coords: x:" + x + ", y:" + y);
        }

        return field[y*sizeX+ x];
    }

    private void cleanDirtyTitles(){
        if(dirtyTitles.isEmpty()){
            return;
        } else {
            for (FieldTitle title : dirtyTitles){
                title.makeEmpty();
            }
        }
        dirtyTitles.clear();
    }


    @Override
    public SnakesProto.GameState.Snake recalculateSnake(SnakesProto.GameState.Snake snake){
        SnakesProto.GameState.Snake.Builder builder = SnakesProto.GameState.Snake.newBuilder(snake);

        SnakesProto.GameState.Coord head = snake.getPoints(0);
        SnakesProto.GameState.Coord newHead = getCoordByDirection(snake.getHeadDirection(),head);

        if(getTitle(newHead.getX(), newHead.getY()).isFood()){
            builder.addPoints(0, newHead);
            builder.setPoints(1, getCordDiff(head, newHead));
        } else {
            builder.setPoints(0, newHead);
            builder.setPoints(1, getCordDiff(head, newHead));
            for (int ind = snake.getPointsCount() - 1; ind > 1; ind --){
                builder.setPoints(ind, snake.getPoints(ind - 1));
            }
        }

        return builder.build();
    }

    private SnakesProto.GameState.Coord getCordDiff(SnakesProto.GameState.Coord coord1, SnakesProto.GameState.Coord coord2){
        SnakesProto.GameState.Coord.Builder coordBuilder = SnakesProto.GameState.Coord.newBuilder();
        coordBuilder.setX( coord1.getX() - coord2.getX());
        coordBuilder.setY(coord1.getY() - coord2.getY());
        return coordBuilder.build();
    }
    public SnakesProto.GameState.Coord getCoordByDirection(SnakesProto.Direction direction, SnakesProto.GameState.CoordOrBuilder coord){
        SnakesProto.GameState.Coord.Builder builder = SnakesProto.GameState.Coord.newBuilder();
        switch (direction){
            case UP -> {
                if(coord.getY() == 0){
                    builder.setY(sizeY - 1);
                } else {
                    builder.setY(coord.getY() - 1);
                }
                builder.setX(coord.getX());
            }
            case DOWN -> {
                if(coord.getY() == sizeY -1){
                    builder.setY(0);
                } else {
                    builder.setY(coord.getY() + 1);
                }
                builder.setX(coord.getX());
            }
            case LEFT -> {
                if(coord.getX() == 0){
                    builder.setX(sizeX - 1);
                } else {
                    builder.setX(coord.getX() - 1);
                }
                builder.setY(coord.getY());
            }
            case RIGHT -> {
                if(coord.getX() == sizeX - 1){
                    builder.setX(0);
                } else {
                    builder.setX(coord.getX() + 1);
                }
                builder.setY(coord.getY());
            }
        }
        return builder.build();
    }
    @Override
    public int getSizeX() {
        return sizeX;
    }

    @Override
    public int getSizeY() {
        return sizeY;
    }

    @Override
    public FieldTitle[] getField() {
        return field;
    }

}
