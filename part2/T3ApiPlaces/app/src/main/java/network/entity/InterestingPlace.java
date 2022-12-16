package network.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@Getter
@Setter

public class InterestingPlace {
    private String name;
    private String xid;
    private Coordinates point;

    @Override
    public String toString() {
        return "Place name: " + name
                + "\nCoords: lat(" + point.getLat() + "), lng(" + point.getLng() +")";
    }
}
