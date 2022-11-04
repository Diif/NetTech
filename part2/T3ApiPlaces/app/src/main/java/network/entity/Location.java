package network.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.text.DecimalFormat;

@NoArgsConstructor
@Getter
@Setter
public class Location {
    private Coordinates point;
    private String name;
    private String country;
    private String city;

    @Override
    public String toString() {
        if(null == point){
            return name + ", " + country;
        }
        return name + ", " + country + "(lat: " + new DecimalFormat("#0.000").format(point.getLat()) + ", lng: " + new DecimalFormat("#0.000").format(point.getLng()) + ")";
    }
}
