package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class CrossingPointsResponse {

    public Map<Long, CrossingsPerTrack> crossings = new HashMap<>();

    public List<Segment> segmentsStats = new ArrayList<>();

    public List<TriggerPoint> triggerPoints = new ArrayList<>();

    /**
     * Number of tracks passing through each individual trigger zone.
     * Key: trigger point name (e.g. "A", "B"), Value: track count.
     * Useful for showing users which zones have no tracks nearby.
     */
    public Map<String, Integer> tracksPerZone = new HashMap<>();

}
