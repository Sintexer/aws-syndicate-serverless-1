package com.task08.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class Units {
    private String time;
    private String temperature_2m;
    private String relative_humidity_2m;
    private String wind_speed_10m;
    private String interval;
}