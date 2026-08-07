package com.example.avalon.runtime.disclosure;

import com.example.avalon.persistence.model.GameEventRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DefaultDisclosurePolicy implements DisclosurePolicy {
    private static final Set<String> PRIVATE_EVENT_TYPES = Set.of("ROLE_ASSIGNED", "MISSION_ACTION_CAST");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Override
    public Optional<GameEventRecord> publicEvent(GameEventRecord event) {
        if (event == null || !"PUBLIC".equalsIgnoreCase(event.visibility()) || PRIVATE_EVENT_TYPES.contains(event.type())) {
            return Optional.empty();
        }
        if (!"MISSION_RESULT_REVEALED".equals(event.type())) {
            return Optional.of(event);
        }
        Map<String, Object> source = read(event.payloadJson());
        Map<String, Object> projected = new LinkedHashMap<>();
        copy(source, projected, "roundNo");
        copy(source, projected, "result");
        copy(source, projected, "failCount");
        return Optional.of(new GameEventRecord(event.eventId(), event.gameId(), event.seqNo(), event.type(),
                event.phase(), event.actorPlayerId(), event.visibility(), write(projected), event.createdAt()));
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) target.put(key, source.get(key));
    }

    private Map<String, Object> read(String value) {
        try { return json.readValue(value == null ? "{}" : value, new TypeReference<Map<String, Object>>() { }); }
        catch (Exception exception) { return Map.of(); }
    }

    private String write(Map<String, Object> value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize public event projection", exception); }
    }
}
