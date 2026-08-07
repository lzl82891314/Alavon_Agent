package com.example.avalon.persistence.store;

import com.example.avalon.persistence.mapper.PersistenceEntityMapper;
import com.example.avalon.persistence.model.ModelProfileRecord;
import com.example.avalon.persistence.repository.ModelProfileRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaModelProfileStore implements ModelProfileStore {
    private final ModelProfileRepository repository;

    public JpaModelProfileStore(ModelProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public ModelProfileRecord save(ModelProfileRecord record) {
        return PersistenceEntityMapper.toRecord(repository.save(PersistenceEntityMapper.toEntity(record)));
    }

    @Override
    public List<ModelProfileRecord> findAll() {
        return repository.findAllByOrderByCreatedAtAsc().stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public Optional<ModelProfileRecord> findByModelId(String modelId) {
        return repository.findById(modelId).map(PersistenceEntityMapper::toRecord);
    }

    @Override
    public void deleteByModelId(String modelId) {
        repository.deleteById(modelId);
    }
}
