package com.example.avalon.persistence.repository;

import com.example.avalon.persistence.entity.ModelProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelProfileRepository extends JpaRepository<ModelProfileEntity, String> {
    List<ModelProfileEntity> findAllByOrderByCreatedAtAsc();
}
