package com.example.avalon.agent.cognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Validates and persists only structured cognition, never a model's raw chain-of-thought. */
@Service
public final class CognitionCommitService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public CognitionCommitService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void commit(String gameId, String playerId, PrivateCognitionDraft draft, long acceptedSequence) {
        if (draft == null || draft.sourceSequence() != acceptedSequence) {
            throw new IllegalArgumentException("Cognition draft must match the accepted rule-event sequence");
        }
        jdbc.update("insert into player_cognition_snapshot (snapshot_id,game_id,player_id,based_on_event_seq_no,belief_json,strategy_json,communication_plan_json,created_at) values (?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), gameId, playerId, acceptedSequence,
                write(draft.beliefs()), write(draft.strategy()), write(draft.communication()), Timestamp.from(Instant.now()));
    }

    public Optional<PrivateCognitionDraft> findLatest(String gameId, String playerId) {
        return jdbc.query("select * from player_cognition_snapshot where game_id=? and player_id=? order by based_on_event_seq_no desc limit 1", rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(new PrivateCognitionDraft(
                    read(rs.getString("belief_json"), BeliefState.class),
                    read(rs.getString("strategy_json"), StrategyState.class),
                    read(rs.getString("communication_plan_json"), CommunicationPlan.class),
                    rs.getLong("based_on_event_seq_no")));
        }, gameId, playerId);
    }

    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Cannot serialize cognition", e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException("Cannot deserialize cognition", e); } }
}
