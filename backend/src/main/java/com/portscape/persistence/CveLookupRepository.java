package com.portscape.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CveLookupRepository extends JpaRepository<CveLookupEntity, String> {
}
