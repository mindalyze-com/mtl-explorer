package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CrossingsPerTrack {

    public GpsTrack gpsTrack;

    List<Crossing> crossings = new ArrayList<>();

}
