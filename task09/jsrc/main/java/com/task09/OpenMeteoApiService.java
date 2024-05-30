package com.task09;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task09.model.WeatherData;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenMeteoApiService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherData getCurrentWeather() throws IOException, InterruptedException {

        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(
                        URI.create("https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&hourly=temperature_2m"))
                .header("accept", "application/json")
                .GET()
                .build();
        var httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(httpResponse.body(), WeatherData.class);

    }

}
