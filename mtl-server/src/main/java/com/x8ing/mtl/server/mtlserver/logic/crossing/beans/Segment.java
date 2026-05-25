package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonPropertyOrder({
        "point1",
        "point2",
        "label",
        "count",
        "timeDelta"
})
public class Segment {

    public String point1;

    public String point2;

    public String label;

    public int count;

    public double timeDelta;


}
