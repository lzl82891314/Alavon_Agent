package com.example.avalon.api.controller;

import com.example.avalon.api.dto.ModelProfileRequest;
import com.example.avalon.api.dto.ModelProfileProbeRequest;
import com.example.avalon.api.dto.ModelProfileProbeResponse;
import com.example.avalon.api.dto.ModelProfileResponse;
import com.example.avalon.api.service.ModelProfileCatalogService;
import com.example.avalon.api.service.ModelProfileProbeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/model-profiles")
public class ModelProfileController {
    private final ModelProfileCatalogService modelProfileCatalogService;
    private final ModelProfileProbeService modelProfileProbeService;

    public ModelProfileController(ModelProfileCatalogService modelProfileCatalogService,
                                  ModelProfileProbeService modelProfileProbeService) {
        this.modelProfileCatalogService = modelProfileCatalogService;
        this.modelProfileProbeService = modelProfileProbeService;
    }

    @GetMapping
    public List<ModelProfileResponse> listModelProfiles() {
        return modelProfileCatalogService.listAll();
    }

    @GetMapping("/{modelId}")
    public ModelProfileResponse getModelProfile(@PathVariable("modelId") String modelId) {
        return modelProfileCatalogService.get(modelId);
    }

    @PostMapping("/{modelId}/probe")
    public ModelProfileProbeResponse probeModelProfile(@PathVariable("modelId") String modelId,
                                                       @RequestBody(required = false) ModelProfileProbeRequest request) {
        return modelProfileProbeService.probe(modelId, request);
    }

    @PostMapping
    public ModelProfileResponse createModelProfile(@RequestBody ModelProfileRequest request) {
        return modelProfileCatalogService.create(request);
    }

    @PutMapping("/{modelId}")
    public ModelProfileResponse updateModelProfile(@PathVariable("modelId") String modelId,
                                                   @RequestBody ModelProfileRequest request) {
        return modelProfileCatalogService.update(modelId, request);
    }

    @DeleteMapping("/{modelId}")
    public void deleteModelProfile(@PathVariable("modelId") String modelId) {
        modelProfileCatalogService.delete(modelId);
    }
}
