package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import lombok.Data;

import java.util.Map;

@Data
public class FilterRequestBean {

    private String filterName;

    private Map<String, String> params;

}
