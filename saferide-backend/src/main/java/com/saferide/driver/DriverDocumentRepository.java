package com.saferide.driver;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {

    List<DriverDocument> findByDriverIdOrderByUploadedAtDesc(Long driverId);
}
