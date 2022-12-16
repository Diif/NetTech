package network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import network.entity.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Log
public class ApiHelper {

    static final HttpClient client = HttpClient.newHttpClient();
    static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static CompletableFuture<List<Location>> getLocationsByName(String name){
        HttpRequest request = HttpRequest.newBuilder()
                .uri(createGraphHopperUri(name))
                .GET()
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(ApiHelper::parseGrapphopperString);
    }

    public static CompletableFuture<Weather> getLocationWeather(Location location){
        HttpRequest request = HttpRequest.newBuilder()
                .uri(createOpenWeatherUri(location))
                .GET()
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(ApiHelper::parseOpenWeatherString);
    }

    public static CompletableFuture<ArrayList<InterestingPlace>> getLocationInterestingPlaces(Location location){
        HttpRequest request = HttpRequest.newBuilder()
                .uri(createOpenTripInterestingPlacesUri(location))
                .GET()
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(ApiHelper::parseOpenTripInterestingPlacesString);
    }

    public static CompletableFuture<InterestingPlaceDetailed> getInterestingPlaceDetails(InterestingPlace place){
        HttpRequest request = HttpRequest.newBuilder()
                .uri(createOpenTripPlaceDetailsUri(place))
                .GET()
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(ApiHelper::parseOpenTripPlaceDetailsString);
    }


    private static List<Location> parseGrapphopperString(String json){
        try {
            JsonNode rootNode = mapper.readTree(json);
            JsonNode hitsNode = rootNode.get("hits");
            log.info("ApiHelper: " + hitsNode.toString());
            return mapper.readValue(hitsNode.toString(), new TypeReference<List<Location>>() {
            });
        } catch (JsonProcessingException e){
            log.warning("ApiHelper: can't parse json - " + e.getLocalizedMessage());
            return null;
        }
    }

    private static Weather parseOpenWeatherString(String json){
        try {
            Weather weather = new Weather();
            JsonNode rootNode = mapper.readTree(json);
            weather.setDescription(rootNode.get("weather").get("0").get("main").toString());
            weather.setTemp(rootNode.get("main").get("temp").asDouble(-100));
            return weather;
        } catch (JsonProcessingException e){
            log.warning("ApiHelper: can't parse json - " + e.getLocalizedMessage());
            return null;
        }
    }

    private static ArrayList<InterestingPlace> parseOpenTripInterestingPlacesString(String json) {
        try {
            JsonNode rootNode = mapper.readTree(json);
            ArrayNode featuresNode =(ArrayNode) rootNode.get("features");
            ArrayList<InterestingPlace> interestingPlaces = new ArrayList<>();
            for (JsonNode node : featuresNode){
                InterestingPlace interestingPlace = new InterestingPlace();

                JsonNode geometryNode = node.get("geometry");
                JsonNode pointNode = geometryNode.get("coordinates");
                Coordinates point = new Coordinates();
                point.setLng(pointNode.get(0).asDouble(-100));
                point.setLat(pointNode.get(1).asDouble(-100));

                JsonNode propertiesNode = node.get("properties");
                interestingPlace.setName(propertiesNode.get("name").toString());
                interestingPlace.setXid(propertiesNode.get("xid").toString());

                interestingPlace.setPoint(point);
                interestingPlaces.add(interestingPlace);
            }
            return interestingPlaces;
        } catch (JsonProcessingException e){
            log.warning("ApiHelper: can't parse json - " + e.getLocalizedMessage());
            return null;
        }
    }


    private static InterestingPlaceDetailed parseOpenTripPlaceDetailsString(String json){
        try {
            log.info("ApiHelper: get details - " + json);
            return mapper.readValue(json, InterestingPlaceDetailed.class);
        } catch (JsonProcessingException e){
            log.warning("ApiHelper: can't parse json - " + e.getLocalizedMessage());
            return null;
        }

    }

    private static URI createGraphHopperUri(String name){
        String adr = "https://graphhopper.com/api/1/geocode?locale=en&q="
                + name.replace(' ', '+')
                + "&key="
                + ApiConsts.GRAPHHOPPER_KEY;
        log.info("ApiHelper: create uri - " + adr);
        return URI.create(adr);
    }

    private static URI createOpenWeatherUri(Location location){
        String adr = "https://api.openweathermap.org/data/2.5/weather?lat="
                + location.getPoint().getLat()
                + "&lon="
                + location.getPoint().getLng()
                + "&units=metric"
                + "&appid="
                + ApiConsts.OPENWEATHER_KEY;
        log.info("ApiHelper: create uri - " + adr);
        return URI.create(adr);
    }

    private static URI createOpenTripInterestingPlacesUri(Location location){
        String adr = "https://api.opentripmap.com/0.1/en/places/radius?radius="
                + ApiConsts.INTERESTING_PLACES_RADIUS
                + "&lon="
                + location.getPoint().getLng()
                + "&lat="
                + location.getPoint().getLat()
                + "&apikey="
                + ApiConsts.OPENTRIP_KEY;
        log.info("ApiHelper: create uri - " + adr);
        return URI.create(adr);
    }

    private static URI createOpenTripPlaceDetailsUri(InterestingPlace interestingPlace){
        String adr = "https://api.opentripmap.com/0.1/en/places/xid/"
                + interestingPlace.getXid().replace("\"","")
                + "?apikey="
                + ApiConsts.OPENTRIP_KEY;
        log.info("ApiHelper: create uri - " + adr);
        System.out.println();
        return URI.create(adr);
    }
}
