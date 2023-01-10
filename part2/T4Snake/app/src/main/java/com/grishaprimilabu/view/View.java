package com.grishaprimilabu.view;

import com.grishaprimilabu.model.PlayersInfo;
import com.grishaprimilabu.model.field.Field;
import com.grishaprimilabu.proto.SnakesProto;

public interface View {
    void updateView(SnakesProto.GamePlayers players);

    void recreateField(Field field);



//    void apply
}
