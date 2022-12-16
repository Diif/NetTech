package network.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class InterestingPlaceDetailed extends InterestingPlace{

    private String name="-";
    private String rate= "-";
    private Address address;
    private String wikipedia= "-";

    private String kinds="-";
    @Getter
    @Setter
    @NoArgsConstructor
    class Address{
        private String country = "-";
        private String city= "-";
        private String road="-";
        private String pedestrian= "-";
        private String house= "-";
        private String postcode= "-";
    }

    @Override
    public String toString() {
        return    "Name: " + name
                + "\nRate: " + rate
                + "\nCountry: " + address.getCountry()
                + "\nCity: " + address.getCity()
                + "\nRoad: " + address.getRoad()
                + "\nPedestrian: " + address.getPedestrian()
                + "\nHouse: " + address.getHouse()
                + "\nPostcode: " + address.getPostcode()
                + "\nWikipedia: " + wikipedia
                + "\nKinds: " + kinds;
    }
}
