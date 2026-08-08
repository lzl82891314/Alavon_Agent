package com.example.avalon.runtime.coordination;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.AssassinationAction;
import com.example.avalon.core.game.model.MissionAction;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PublicSpeechAction;
import com.example.avalon.core.game.model.TeamProposalAction;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable action collector. Database writes are deliberately short and transactional at one batch boundary. */
public final class SqliteActionCollector implements ActionCollector {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public SqliteActionCollector(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public synchronized ActionBatch open(NextRequirement requirement) {
        if (findActive(requirement.gameId()).isPresent()) throw new IllegalStateException("Game already has an active action batch");
        ActionBatch batch = create(requirement);
        jdbc.update("insert into action_batch (batch_id,game_id,source_game_version,turn_token,phase,action_type,required_players_json,status,batch_version,created_at,deadline) values (?,?,?,?,?,?,?,?,?,?,?)",
                batch.batchId(), batch.gameId(), batch.sourceGameVersion(), batch.turnToken(), batch.phase().name(), batch.actionType(), write(batch.requiredPlayers()), batch.status().name(), batch.batchVersion(), Timestamp.from(batch.createdAt()), Timestamp.from(batch.deadline()));
        return batch;
    }

    @Override public synchronized SubmissionResult submit(ActionSubmission submission) {
        ActionBatch batch = require(submission.batchId());
        boolean replay = batch.submissions().containsKey(submission.playerId()) && batch.submissions().get(submission.playerId()).idempotencyKey().equals(submission.idempotencyKey());
        batch.submit(submission);
        if (!replay) jdbc.update("insert into action_submission (batch_id,player_id,idempotency_key,expected_batch_version,controller_execution_id,action_json,result_json,submitted_at) values (?,?,?,?,?,?,?,?)",
                submission.batchId(), submission.playerId(), submission.idempotencyKey(), submission.expectedBatchVersion(), submission.controllerExecutionId(), writeAction(submission.action()), writeResult(submission.actionResult()), Timestamp.from(submission.submittedAt()));
        saveBatch(batch);
        return new SubmissionResult(batch, true, replay, replay ? "idempotent replay" : "accepted");
    }

    @Override public Optional<ActionBatch> findActive(String gameId) {
        List<ActionBatch> batches = jdbc.query("select * from action_batch where game_id=? and status in ('OPEN','PARTIALLY_COLLECTED','COMPLETED') order by created_at desc", (rs, row) -> load(rs.getString("batch_id")), gameId);
        return batches.stream().findFirst();
    }
    @Override public synchronized ActionBatch expire(String batchId, Instant now) { ActionBatch batch=require(batchId); batch.expire(now); saveBatch(batch); return batch; }
    @Override public synchronized ActionBatch invalidate(String batchId, String reason) { ActionBatch batch=require(batchId); batch.invalidate(reason); saveBatch(batch); return batch; }
    @Override public synchronized void markCommitted(String batchId) { ActionBatch batch=require(batchId); batch.markCommitted(); saveBatch(batch); }

    private ActionBatch require(String id) { return load(id); }
    private ActionBatch load(String id) {
        return jdbc.query("select * from action_batch where batch_id=?", rs -> rs.next() ? hydrate(rs) : null, id);
    }
    private ActionBatch hydrate(java.sql.ResultSet rs) throws java.sql.SQLException {
        String id=rs.getString("batch_id");
        Map<String, ActionSubmission> submissions = new LinkedHashMap<>();
        jdbc.query("select * from action_submission where batch_id=? order by submitted_at", row -> {
            String player = row.getString("player_id");
            PlayerAction action = readAction(row.getString("action_json"));
            submissions.put(player, new ActionSubmission(id, player, action, row.getString("idempotency_key"), row.getLong("expected_batch_version"), row.getString("controller_execution_id"), row.getTimestamp("submitted_at").toInstant(), readResult(row.getString("result_json"), action)));
        }, id);
        return ActionBatch.restore(id, rs.getString("game_id"), rs.getLong("source_game_version"), rs.getString("turn_token"), GamePhase.valueOf(rs.getString("phase")), rs.getString("action_type"), new LinkedHashSet<>(read(rs.getString("required_players_json"))), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("deadline").toInstant(), ActionBatchStatus.valueOf(rs.getString("status")), rs.getLong("batch_version"), submissions);
    }
    private void saveBatch(ActionBatch b) { jdbc.update("update action_batch set status=?,batch_version=? where batch_id=?", b.status().name(), b.batchVersion(), b.batchId()); }
    private ActionBatch create(NextRequirement r) {
        if (r instanceof SinglePlayerActionRequirement x) return new ActionBatch(newBatchId(r.gameId()),r.gameId(),r.sourceGameVersion(),r.gameId()+"-turn-"+r.sourceGameVersion(),r.phase(),x.actionType(),java.util.Set.of(x.playerId()),x.deadline());
        if (r instanceof ParallelPlayerActionRequirement x) return new ActionBatch(newBatchId(r.gameId()),r.gameId(),r.sourceGameVersion(),r.gameId()+"-turn-"+r.sourceGameVersion(),r.phase(),x.actionType(),x.requiredPlayers(),x.deadline());
        if (r instanceof ExternalPlayerActionRequirement x) return new ActionBatch(newBatchId(r.gameId()),r.gameId(),r.sourceGameVersion(),r.gameId()+"-turn-"+r.sourceGameVersion(),r.phase(),x.actionType(),new LinkedHashSet<>(x.requiredPlayers()),x.deadline());
        throw new IllegalArgumentException("Only player actions create batches");
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private List<String> read(String value) { try { return json.readValue(value, new TypeReference<List<String>>() {}); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String writeAction(PlayerAction action) { Map<String,Object> value=new LinkedHashMap<>(); value.put("type",action.type()); if(action instanceof PublicSpeechAction x){value.put("speech",x.speechText());value.put("speechAct",x.speechAct());value.put("mentions",x.mentions());value.put("replyToEventSequences",x.replyToEventSequences());} else if(action instanceof TeamProposalAction x)value.put("players",x.selectedPlayerIds()); else if(action instanceof TeamVoteAction x)value.put("vote",x.vote().name()); else if(action instanceof MissionAction x)value.put("choice",x.choice().name()); else if(action instanceof AssassinationAction x)value.put("target",x.targetPlayerId()); return write(value); }
    private String writeResult(com.example.avalon.core.game.model.PlayerActionResult result) { if (result == null) return null; Map<String,Object> value=new LinkedHashMap<>(); value.put("publicSpeech",result.publicSpeech()); value.put("auditReason",result.auditReason()); value.put("memoryUpdate",result.memoryUpdate()); value.put("rawMetadata",result.rawMetadata()); return write(value); }
    private com.example.avalon.core.game.model.PlayerActionResult readResult(String value, PlayerAction action) { if (value == null || value.isBlank()) return null; try { Map<String,Object> result=json.readValue(value,new TypeReference<Map<String,Object>>(){}); return new com.example.avalon.core.game.model.PlayerActionResult((String)result.get("publicSpeech"), action, result.get("auditReason") == null ? null : json.convertValue(result.get("auditReason"), com.example.avalon.core.player.memory.AuditReason.class), result.get("memoryUpdate") == null ? null : json.convertValue(result.get("memoryUpdate"), com.example.avalon.core.player.memory.MemoryUpdate.class), result.get("rawMetadata") == null ? Map.of() : json.convertValue(result.get("rawMetadata"),new TypeReference<Map<String,Object>>(){})); } catch(Exception e) { throw new IllegalStateException("Cannot deserialize persisted action result", e); } }
    private PlayerAction readAction(String value) { try { Map<String,Object> a=json.readValue(value,new TypeReference<Map<String,Object>>(){}); return switch(String.valueOf(a.get("type"))){ case "PUBLIC_SPEECH" -> new PublicSpeechAction(String.valueOf(a.get("speech")),String.valueOf(a.getOrDefault("speechAct","STATE_OPINION")),json.convertValue(a.getOrDefault("mentions",List.of()),new TypeReference<List<String>>(){}),json.convertValue(a.getOrDefault("replyToEventSequences",List.of()),new TypeReference<List<Long>>(){})); case "TEAM_PROPOSAL" -> new TeamProposalAction(json.convertValue(a.get("players"),new TypeReference<List<String>>(){})); case "TEAM_VOTE" -> new TeamVoteAction(VoteChoice.valueOf(String.valueOf(a.get("vote")))); case "MISSION_ACTION" -> new MissionAction(MissionChoice.valueOf(String.valueOf(a.get("choice")))); case "ASSASSINATION" -> new AssassinationAction(String.valueOf(a.get("target"))); default -> throw new IllegalArgumentException("Unsupported stored action");};}catch(Exception e){throw new IllegalStateException(e);} }
}
