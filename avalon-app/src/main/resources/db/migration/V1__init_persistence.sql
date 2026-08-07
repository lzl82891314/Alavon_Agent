create table game_event (
    event_id varchar(64) primary key,
    game_id varchar(64) not null,
    seq_no bigint not null,
    type varchar(128) not null,
    phase varchar(64),
    actor_player_id varchar(64),
    visibility varchar(32),
    payload_json clob,
    created_at timestamp not null
);

create unique index idx_game_event_game_seq on game_event (game_id, seq_no);
create index idx_game_event_game_type_seq on game_event (game_id, type, seq_no);
create index idx_game_event_game_actor_seq on game_event (game_id, actor_player_id, seq_no);
create index idx_game_event_game_created on game_event (game_id, created_at);

create table game_snapshot (
    snapshot_id varchar(64) primary key,
    game_id varchar(64) not null,
    based_on_event_seq_no bigint not null,
    round_no integer,
    phase varchar(64),
    state_json clob,
    created_at timestamp not null
);

create unique index idx_game_snapshot_game_seq on game_snapshot (game_id, based_on_event_seq_no);
create index idx_game_snapshot_game_round on game_snapshot (game_id, round_no);
create index idx_game_snapshot_game_created on game_snapshot (game_id, created_at);

create table player_memory_snapshot (
    snapshot_id varchar(64) primary key,
    game_id varchar(64) not null,
    player_id varchar(64) not null,
    based_on_event_seq_no bigint not null,
    memory_json clob,
    created_at timestamp not null
);

create unique index idx_player_memory_game_player_seq on player_memory_snapshot (game_id, player_id, based_on_event_seq_no);
create index idx_player_memory_game_player_created on player_memory_snapshot (game_id, player_id, created_at);
create index idx_player_memory_game_created on player_memory_snapshot (game_id, created_at);

create table audit_record (
    audit_id varchar(64) primary key,
    game_id varchar(64) not null,
    event_seq_no bigint,
    player_id varchar(64),
    visibility varchar(32),
    input_context_json clob,
    input_context_hash varchar(128),
    raw_model_response_json clob,
    parsed_action_json clob,
    audit_reason_json clob,
    validation_result_json clob,
    error_message clob,
    created_at timestamp not null
);

create index idx_audit_game_created on audit_record (game_id, created_at);
create index idx_audit_game_event on audit_record (game_id, event_seq_no);
create index idx_audit_game_player_created on audit_record (game_id, player_id, created_at);
create index idx_audit_game_visibility_created on audit_record (game_id, visibility, created_at);

create table model_profile (
    model_id varchar(128) primary key,
    display_name varchar(256) not null,
    provider varchar(64) not null,
    model_name varchar(128) not null,
    temperature double precision,
    max_tokens integer,
    provider_options_json clob,
    enabled boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_model_profile_enabled_created on model_profile (enabled, created_at);
create index idx_model_profile_provider_model on model_profile (provider, model_name);
