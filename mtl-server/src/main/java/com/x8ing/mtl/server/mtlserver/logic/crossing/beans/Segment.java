package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Segment {

    public String point1;

    public String point2;

    public String label;

    public int count;

    public double timeDelta;


}
