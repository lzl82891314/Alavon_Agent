create table action_batch (
    batch_id varchar(128) primary key,
    game_id varchar(64) not null,
    source_game_version bigint not null,
    turn_token varchar(192) not null,
    phase varchar(64) not null,
    action_type varchar(64) not null,
    required_players_json text not null,
    status varchar(32) not null,
    batch_version bigint not null,
    created_at timestamp not null,
    deadline timestamp not null,
    invalidation_reason text
);

create index idx_action_batch_game_status on action_batch (game_id, status);
create unique index idx_action_batch_game_turn on action_batch (game_id, turn_token);

create table action_submission (
    batch_id varchar(128) not null,
    player_id varchar(64) not null,
    idempotency_key varchar(192) not null,
    expected_batch_version bigint not null,
    controller_execution_id varchar(192),
    action_json text not null,
    submitted_at timestamp not null,
    primary key (batch_id, player_id),
    unique (batch_id, idempotency_key),
    foreign key (batch_id) references action_batch(batch_id)
);

create table player_cognition_snapshot (
    snapshot_id varchar(64) primary key,
    game_id varchar(64) not null,
    player_id varchar(64) not null,
    based_on_event_seq_no bigint not null,
    belief_json text not null,
    strategy_json text not null,
    communication_plan_json text not null,
    created_at timestamp not null
);

create unique index idx_player_cognition_game_player_seq
    on player_cognition_snapshot (game_id, player_id, based_on_event_seq_no);
