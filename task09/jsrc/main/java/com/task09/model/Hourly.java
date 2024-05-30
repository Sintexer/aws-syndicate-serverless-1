package com.task09.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class Hourly {
    private List<String> time;
    private List<Double> temperature_2m;
}
