package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface SimplifiedTrackRepository extends JpaRepository<SimplifiedTrack, Long> {

    //                 'fake_' || EXTRACT(epoch FROM NOW() ) || '_' || random()*1000000::integer AS fake_id ,
    @QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.CACHEABLE, value = "false"))
    @Query(nativeQuery = true, value = """
            select
                EXTRACT(epoch FROM CURRENT_TIMESTAMP ) || '_' || random()*1000000 AS fake_id,
            	gt.id gps_track_id, gtd.id gps_track_data_id, gtd.track_type track_type,
            	ST_SnapToGrid((st_transform(ST_Simplify(st_transform(track,	3857),	:tolerance),	4326)),	0.00001) as line_string
            from
            	gps_track gt
            	left join gps_track_data gtd  on gt.id = gtd.gps_track_id
            where gt.id = :id and gtd.track_type = 'RAW_OUTLIER_CLEANED'
            """)
    SimplifiedTrack getTrackSimplified(@Param(value = "tolerance") BigDecimal tolerance, @Param(value = "id") Long id);
}
