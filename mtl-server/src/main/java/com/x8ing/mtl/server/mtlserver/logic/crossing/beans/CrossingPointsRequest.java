package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

import com.x8ing.mtl.server.mtlserver.web.services.track.entity.FilterRequestBean;
import lombok.Data;

import java.util.List;

@Data
public class CrossingPointsRequest {

    public List<TriggerPoint> triggerPoints;

    /**
     * The radius around each trigger point that defines the crossing zone.
     * A crossing is detected when the track enters this zone (transitions from outside to inside).
     */
    public Double radius;

    /**
     * Optional
     */
    public FilterRequestBean filter;
}
