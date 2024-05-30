package com.task08.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WeatherData {
    private double latitude;
    private double longitude;
    private double generationtime_ms;
    private int utc_offset_seconds;
    private String timezone;
    private String timezone_abbreviation;
    private double elevation;
    private Units hourly_units;
    private Hourly hourly;
    private Units current_units;
    private CurrentWeather current;
}
