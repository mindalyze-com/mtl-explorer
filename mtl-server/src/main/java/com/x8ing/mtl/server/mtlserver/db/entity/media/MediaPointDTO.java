package com.x8ing.mtl.server.mtlserver.db.entity.media;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaPointDTO {
    private Long id;
    private Double lat;
    private Double lng;
}
