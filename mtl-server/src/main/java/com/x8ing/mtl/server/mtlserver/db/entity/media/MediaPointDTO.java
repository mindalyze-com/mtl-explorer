package com.x8ing.mtl.server.mtlserver.db.entity.media;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "lat",
        "lng"
})
public class MediaPointDTO {
    private Long id;
    private Double lat;
    private Double lng;
}
