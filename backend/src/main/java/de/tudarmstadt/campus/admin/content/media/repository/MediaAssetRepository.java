package de.tudarmstadt.campus.admin.content.media.repository;

import de.tudarmstadt.campus.admin.content.media.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByPoiId(Long poiId);
}
