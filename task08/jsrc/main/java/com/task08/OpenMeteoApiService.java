package com.task08;

import com.google.gson.Gson;
import com.task08.model.WeatherData;
import lombok.SneakyThrows;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenMeteoApiService {

    private final Gson gson = new Gson();

    @SneakyThrows
    public WeatherData getCurrentWeather() {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(
                        URI.create("https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m"))
                .header("accept", "application/json")
                .GET()
                .build();
        var httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(httpResponse.body(), WeatherData.class);
    }

}
