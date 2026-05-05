-- apply changes
create table parties (
  id                            integer not null,
  world_id                      integer not null default 0,
  leader_id                     integer not null,
  created_at                    datetime(6) not null default current_timestamp(6),
  constraint pk_parties primary key (id)
);

create index idx_parties_world on parties (world_id);
create index idx_parties_leader on parties (leader_id);

-- clean residual party references from characters table
update characters set party = -1 where party > 0;
