package com.task08.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurrentWeather {
    private String time;
    private int interval;
    private double temperature_2m;
    private double wind_speed_10m;
}
