package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.locationtech.jts.geom.Coordinate;

@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonPropertyOrder({
        "name",
        "coordinate"
})
public class TriggerPoint {

    public String name;
    public Coordinate coordinate;

}
