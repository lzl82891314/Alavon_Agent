package com.example.avalon.persistence.store;

import com.example.avalon.persistence.model.ModelProfileRecord;

import java.util.List;
import java.util.Optional;

public interface ModelProfileStore {
    ModelProfileRecord save(ModelProfileRecord record);

    List<ModelProfileRecord> findAll();

    Optional<ModelProfileRecord> findByModelId(String modelId);

    void deleteByModelId(String modelId);
}
