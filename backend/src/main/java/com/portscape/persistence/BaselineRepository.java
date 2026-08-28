package com.portscape.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BaselineRepository extends JpaRepository<BaselineEntity, String> {

    List<BaselineEntity> findAllByOrderByTargetAsc();
}
