-- apply changes
create table aclog (
  id                            integer auto_increment not null,
  account_id                    integer not null,
  boss_id                       varchar(255) not null,
  last_attempt                  datetime(6) not null,
  constraint uq_aclog_account_id unique (account_id),
  constraint pk_aclog primary key (id)
);

create table accounts (
  id                            integer auto_increment not null comment '账户ID',
  name                          varchar(16) not null comment '账户名',
  password                      varchar(255) not null comment '密码',
  salt                          varchar(255) comment '密码盐',
  second_password               varchar(255) comment '二级密码',
  second_salt                   varchar(255) comment '二级密码盐',
  state                         integer not null comment '登录状态：0-未登录(默认)，1-服务过渡，2-已登录，3-等待，4-商城过渡，5-商城已登录，6-切换频道。',
  last_login                    datetime(6) comment '上次登录的时间戳',
  last_logon                    datetime(6) comment '上次离线的时间戳',
  expiration                    datetime(6),
  gender                        integer not null comment '性别：0-男，1-女，10-未知',
  birthday                      date comment '生日',
  email                         varchar(255) comment '电子邮箱地址',
  mac                           varchar(255) comment '网卡地址',
  session_ip                    varchar(255) comment '会话IP地址',
  gm                            integer not null comment 'GM 等级：0-非GM-普通用户，1-1级GM，2-2级GM，...',
  vip                           integer,
  cash                          bigint,
  m_points                      bigint,
  points                        bigint not null,
  v_points                      bigint not null,
  money                         bigint not null,
  money_b                       bigint not null,
  last_gain_hm                  bigint not null,
  paypal_nx                     integer,
  banned                        tinyint(1) not null comment '是否禁用：0-false-未禁用，!0-true-禁用',
  ban_reason                    varchar(255) comment '禁用原因',
  temp_ban                      datetime(6) comment '临时禁用时间',
  g_reason                      integer,
  created                       datetime(6) not null comment '创建时间',
  modified                      datetime(6) not null comment '修改时间',
  constraint uq_accounts_name unique (name),
  constraint pk_accounts primary key (id)
);

create table accounts_info (
  id                            integer auto_increment not null,
  account_id                    integer not null,
  world_id                      integer not null,
  card_slots                    integer not null,
  game_points                   integer not null,
  updated                       datetime(6),
  game_points_pd                integer not null,
  game_points_ps                integer not null,
  sjrw                          integer not null,
  sgrw                          integer not null,
  fbrw                          integer,
  sbossrw                       integer,
  sgrwa                         integer,
  fbrwa                         integer,
  sbossrwa                      integer,
  lb                            integer,
  constraint uq_accounts_info_account_id unique (account_id),
  constraint pk_accounts_info primary key (id)
);

create table achievements (
  achievement_id                integer not null,
  char_id                       integer not null,
  account_id                    integer not null,
  constraint uq_achievements_account_id unique (account_id),
  constraint pk_achievements primary key (achievement_id,char_id)
);

create table alliances (
  id                            integer auto_increment not null,
  name                          varchar(255) not null,
  leader_id                     integer not null,
  guild1                        integer not null,
  guild2                        integer not null,
  guild3                        integer,
  guild4                        integer,
  guild5                        integer,
  rank1                         varchar(255) not null,
  rank2                         varchar(255) not null,
  rank3                         varchar(255) not null,
  rank4                         varchar(255) not null,
  rank5                         varchar(255) not null,
  capacity                      integer not null,
  notice                        varchar(255) not null,
  constraint uq_alliances_leader_id unique (leader_id),
  constraint uq_alliances_guild1 unique (guild1),
  constraint uq_alliances_guild2 unique (guild2),
  constraint uq_alliances_guild3 unique (guild3),
  constraint uq_alliances_guild4 unique (guild4),
  constraint uq_alliances_guild5 unique (guild5),
  constraint pk_alliances primary key (id)
);

create table bbs_replies (
  id                            integer auto_increment not null,
  thread_id                     integer not null,
  poster_id                     integer not null,
  timestamp                     datetime(6) not null,
  content                       varchar(255) not null,
  guild_id                      integer,
  constraint pk_bbs_replies primary key (id)
);

create table bbs_threads (
  id                            integer auto_increment not null,
  poster_id                     integer not null,
  name                          varchar(255) not null,
  timestamp                     datetime(6) not null,
  icon                          integer not null,
  start_post                    varchar(255) not null,
  guild_id                      integer,
  local_thread_id               integer not null,
  constraint pk_bbs_threads primary key (id)
);

create table bosslog (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  boss_id                       varchar(255) not null,
  last_attempt                  datetime(6) not null,
  constraint pk_bosslog primary key (id)
);

create table buddies (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  buddy_id                      integer not null,
  pending                       tinyint(1) default 0 not null,
  group_name                    varchar(255) not null,
  constraint uq_buddies_character_id unique (character_id),
  constraint pk_buddies primary key (id)
);

create table cashshop_modified_items (
  id                            integer auto_increment not null,
  name                          varchar(255) not null,
  discount_price                integer not null,
  mark                          integer not null,
  show_up                       tinyint(1) default 0 not null,
  item_id                       integer not null,
  priority                      integer not null,
  package_field                 tinyint(1) default 0 not null,
  period                        integer not null,
  gender                        integer not null,
  count                         integer not null,
  meso                          integer not null,
  unk_1                         integer not null,
  unk_2                         integer not null,
  unk_3                         integer not null,
  extra_flags                   integer not null,
  constraint pk_cashshop_modified_items primary key (id)
);

create table characters (
  id                            integer auto_increment not null comment '角色ID',
  name                          varchar(255) not null comment '角色名字',
  account_id                    integer not null,
  world                         integer not null comment '所在世界：官方版本有四个大区，公益服则没有世界的划分，所以此字段没有使用',
  level                         integer not null comment '等级：目前是 1-255 级',
  exp                           integer not null comment '当前经验值',
  str                           integer not null comment '力量',
  dex                           integer not null comment '敏捷',
  luk                           integer not null comment '运气',
  int_                          integer not null comment '智力',
  hp                            integer not null comment '当前生命值',
  mp                            integer not null comment '当前魔法值',
  max_hp                        integer not null comment '最大生命值',
  max_mp                        integer not null comment '最大魔法值',
  meso                          integer not null,
  hp_ap_used                    integer not null,
  job                           integer not null comment '职业',
  skin_color                    integer not null comment '皮肤颜色',
  gender                        integer not null comment '性别',
  fame                          integer not null comment '人气',
  hair                          integer not null comment '发型',
  face                          integer not null comment '脸型',
  ap                            integer not null comment '魔法力？',
  map                           integer not null comment '地图',
  spawn_point                   integer not null comment '出生点',
  gm                            integer not null,
  party                         integer not null,
  buddy_capacity                integer not null comment '好友数量上限',
  guild_id                      integer,
  guild_rank                    integer not null,
  alliance_rank                 integer not null,
  monster_book_cover            integer not null comment '怪物书封面',
  dojo_pts                      integer not null comment '道场积分',
  dojo_record                   integer not null comment '道场记录',
  pets                          varchar(255) not null comment '宠物',
  sp                            varchar(255) not null,
  subcategory                   integer not null,
  jaguar                        integer not null,
  rank_                         integer not null,
  move_rank                     integer not null,
  job_rank                      integer not null,
  job_move_rank                 integer not null,
  marriage_id                   integer,
  family_id                     integer,
  senior_id                     integer,
  junior1                       integer,
  junior2                       integer,
  current_rep                   integer not null,
  total_rep                     integer not null,
  char_message                  varchar(255) not null,
  expression                    integer not null,
  constellation                 integer not null,
  blood                         integer not null,
  month                         integer not null,
  day                           integer not null,
  beans                         integer not null,
  prefix                        decimal(16,3) not null,
  skillzq                       integer not null,
  grname                        integer not null,
  jzname                        integer not null,
  mrfbrw                        integer not null,
  mrsjrw                        integer not null,
  mrsgrw                        integer not null,
  mrsbossrw                     integer not null,
  hythd                         integer not null,
  mrsgrwa                       integer not null,
  mrfbrwa                       integer not null,
  mrsbossrwa                    integer not null,
  mrsgrws                       integer not null,
  mrsbossrws                    integer not null,
  mrfbrws                       integer not null,
  mrsgrwas                      integer not null,
  mrsbossrwas                   integer not null,
  mrfbrwas                      integer not null,
  ddj                           integer,
  vip                           integer,
  bosslog                       integer,
  djjl                          integer not null,
  qiandao                       integer not null,
  mountid                       integer not null,
  sg                            integer not null,
  created                       datetime(6) not null comment '创建时间',
  constraint uq_characters_marriage_id unique (marriage_id),
  constraint uq_characters_senior_id unique (senior_id),
  constraint uq_characters_junior1 unique (junior1),
  constraint uq_characters_junior2 unique (junior2),
  constraint pk_characters primary key (id)
);

create table character_slots (
  id                            integer auto_increment not null,
  acc_id                        integer not null,
  world_id                      integer not null,
  char_slots                    integer not null,
  constraint pk_character_slots primary key (id)
);

create table cheatlog (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  offense                       varchar(255) not null,
  count                         integer not null,
  last_offense_time             datetime(6) not null,
  param                         varchar(255) not null,
  constraint pk_cheatlog primary key (id)
);

create table csequipment (
  id                            integer auto_increment not null,
  inventory_item_id             integer,
  upgrade_slots                 integer not null,
  level                         integer not null,
  str                           integer not null,
  dex                           integer not null,
  intelligence                  integer not null,
  luk                           integer not null,
  hp                            integer not null,
  mp                            integer not null,
  watk                          integer,
  matk                          integer not null,
  wdef                          integer not null,
  mdef                          integer not null,
  acc                           integer not null,
  avoid                         integer not null,
  hands                         integer not null,
  speed                         integer not null,
  jump                          integer not null,
  vicious_hammer                integer not null,
  item_exp                      integer not null,
  durability                    integer not null,
  enhance                       integer not null,
  potential1                    integer not null,
  potential2                    integer not null,
  potential3                    integer not null,
  hp_r                          integer not null,
  mp_r                          integer not null,
  item_level                    integer not null,
  constraint uq_csequipment_inventory_item_id unique (inventory_item_id),
  constraint pk_csequipment primary key (id)
);

create table csitems (
  id                            integer auto_increment not null,
  character_id                  integer,
  account_id                    integer,
  package_id                    integer,
  item_id                       integer not null,
  inventory_type                integer not null,
  position                      integer not null,
  quantity                      integer not null,
  owner                         varchar(255),
  gm_log                        varchar(255),
  unique_id                     integer not null,
  flag                          integer not null,
  expire_date                   datetime(6) not null,
  type                          integer not null,
  sender                        varchar(255) not null,
  item_level                    integer not null,
  constraint pk_csitems primary key (id)
);

create table drop_data (
  id                            bigint auto_increment not null,
  dropper_id                    integer not null,
  item_id                       integer not null,
  min_quantity                  integer not null,
  max_quantity                  integer not null,
  quest_id                      integer not null,
  chance                        integer not null,
  constraint pk_drop_data primary key (id)
);

create table drop_data_global (
  id                            bigint auto_increment not null,
  continent                     integer not null,
  drop_type                     integer not null,
  item_id                       integer not null,
  min_quantity                  integer not null,
  max_quantity                  integer not null,
  quest_id                      integer not null,
  chance                        integer not null,
  comments                      varchar(255),
  constraint pk_drop_data_global primary key (id)
);

create table drop_data_vana (
  id                            bigint auto_increment not null,
  dropper_id                    integer not null,
  flags                         varchar(255) not null,
  item_id                       integer not null,
  min_quantity                  integer not null,
  max_quantity                  integer not null,
  quest_id                      integer not null,
  chance                        integer not null,
  constraint pk_drop_data_vana primary key (id)
);

create table dueyequipment (
  id                            integer auto_increment not null,
  inventoryitemid               integer not null,
  upgrade_slots                 integer not null,
  level                         integer not null,
  str                           integer not null,
  dex                           integer not null,
  intelligence                  integer not null,
  luk                           integer not null,
  hp                            integer not null,
  mp                            integer not null,
  watk                          integer not null,
  matk                          integer not null,
  wdef                          integer not null,
  mdef                          integer not null,
  acc                           integer not null,
  avoid                         integer not null,
  hands                         integer not null,
  speed                         integer not null,
  jump                          integer not null,
  vicious_hammer                integer not null,
  item_exp                      integer not null,
  durability                    integer not null,
  enhance                       integer not null,
  potential1                    integer not null,
  potential2                    integer not null,
  potential3                    integer not null,
  hp_r                          integer not null,
  mp_r                          integer not null,
  constraint uq_dueyequipment_inventoryitemid unique (inventoryitemid),
  constraint pk_dueyequipment primary key (id)
);

create table dueyitems (
  id                            integer auto_increment not null,
  character_id                  integer,
  account_id                    integer,
  package_id                    integer,
  item_id                       integer not null,
  inventory_type                integer not null,
  position                      integer not null,
  quantity                      integer not null,
  owner                         varchar(255),
  gm_log                        varchar(255),
  unique_id                     integer not null,
  flag                          integer not null,
  expire_date                   datetime(6) not null,
  type                          integer not null,
  sender                        varchar(255) not null,
  constraint uq_dueyitems_package_id unique (package_id),
  constraint pk_dueyitems primary key (id)
);

create table dueypackages (
  id                            integer auto_increment not null,
  receiver_id                   integer not null,
  sender_name                   varchar(255) not null,
  mesos                         integer,
  timestamp                     datetime(6),
  checked                       tinyint(1) default 0 not null,
  type                          integer not null,
  constraint pk_dueypackages primary key (id)
);

create table eventstats (
  id                            integer auto_increment not null,
  event                         varchar(255) not null,
  instance                      varchar(255) not null,
  character_id                  integer not null,
  channel                       integer not null,
  time                          datetime(6) not null,
  constraint pk_eventstats primary key (id)
);

create table famelog (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  characterid_to                integer not null,
  when                          datetime(6) not null,
  constraint uq_famelog_character_id unique (character_id),
  constraint pk_famelog primary key (id)
);

create table families (
  id                            integer auto_increment not null,
  leader_id                     integer not null,
  notice                        varchar(255) not null,
  constraint uq_families_leader_id unique (leader_id),
  constraint pk_families primary key (id)
);

create table fishingjf (
  id                            integer auto_increment not null,
  accname                       varchar(255) not null,
  fishing                       integer not null,
  xx                            integer not null,
  xxx                           integer not null,
  constraint pk_fishingjf primary key (id)
);

create table fishing_rewards (
  id                            integer not null,
  chance                        integer not null,
  expiration                    datetime(6),
  name                          varchar(255)
);

create table game_poll_reply (
  id                            integer auto_increment not null,
  account_id                    integer not null,
  select_ans                    integer not null,
  constraint pk_game_poll_reply primary key (id)
);

create table gifts (
  id                            integer auto_increment not null,
  recipient                     integer not null,
  from_                         varchar(255) not null,
  message                       varchar(255) not null,
  sn                            integer not null,
  unique_id                     integer not null,
  constraint pk_gifts primary key (id)
);

create table gmlog (
  id                            integer auto_increment not null,
  cid                           integer not null,
  command                       varchar(255) not null,
  map_id                        integer not null,
  name                          varchar(255) not null,
  ip                            varchar(255) not null,
  constraint pk_gmlog primary key (id)
);

create table guilds (
  id                            integer auto_increment not null,
  leader                        integer not null,
  name                          varchar(255) not null,
  gp                            integer not null,
  logo                          integer,
  logo_color                    integer not null,
  logo_bg                       integer not null,
  logo_bg_color                 integer not null,
  rank1title                    varchar(255) not null,
  rank2title                    varchar(255) not null,
  rank3title                    varchar(255) not null,
  rank4title                    varchar(255) not null,
  rank5title                    varchar(255) not null,
  capacity                      integer not null,
  notice                        varchar(255),
  signature                     integer not null,
  alliance                      integer,
  constraint uq_guilds_leader unique (leader),
  constraint uq_guilds_alliance unique (alliance),
  constraint pk_guilds primary key (id)
);

create table hiredmerch (
  id                            integer auto_increment not null,
  character_id                  integer,
  account_id                    integer,
  map                           integer not null,
  channel                       integer,
  mesos                         integer,
  time                          datetime(6),
  constraint pk_hiredmerch primary key (id)
);

create table hiredmerchequipment (
  id                            integer auto_increment not null,
  inventory_item_id             integer not null,
  upgrade_slots                 integer not null,
  level                         integer not null,
  str                           integer not null,
  dex                           integer not null,
  intelligence                  integer not null,
  luk                           integer not null,
  hp                            integer not null,
  mp                            integer not null,
  watk                          integer not null,
  matk                          integer not null,
  wdef                          integer not null,
  mdef                          integer not null,
  acc                           integer not null,
  avoid                         integer not null,
  hands                         integer not null,
  speed                         integer not null,
  jump                          integer not null,
  vicious_hammer                integer not null,
  item_exp                      integer not null,
  durability                    integer not null,
  enhance                       integer not null,
  potential1                    integer not null,
  potential2                    integer not null,
  potential3                    integer not null,
  hp_r                          integer not null,
  mp_r                          integer not null,
  item_level                    integer not null,
  constraint uq_hiredmerchequipment_inventory_item_id unique (inventory_item_id),
  constraint pk_hiredmerchequipment primary key (id)
);

create table hiredmerchitems (
  id                            integer auto_increment not null,
  character_id                  integer,
  account_id                    integer,
  package_id                    integer,
  item_id                       integer not null,
  inventory_type                integer not null,
  position                      integer not null,
  quantity                      integer not null,
  owner                         varchar(255),
  gm_log                        varchar(255),
  unique_id                     integer not null,
  flag                          integer not null,
  expire_date                   datetime(6) not null,
  type                          integer not null,
  sender                        varchar(255) not null,
  constraint pk_hiredmerchitems primary key (id)
);

create table htsquads (
  id                            integer auto_increment not null,
  channel                       integer not null,
  leader_id                     integer not null,
  status                        integer not null,
  members                       integer not null,
  constraint pk_htsquads primary key (id)
);

create table hypay (
  id                            integer auto_increment not null,
  acc_name                      varchar(255),
  pay_used                      integer not null,
  pay                           integer not null,
  pay_reward                    integer not null,
  constraint pk_hypay primary key (id)
);

create table ipbans (
  id                            integer auto_increment not null,
  ip                            varchar(255) not null,
  constraint pk_ipbans primary key (id)
);

create table inventoryequipment (
  id                            integer auto_increment not null,
  inventory_item_id             integer not null,
  upgrade_slots                 integer not null,
  level                         integer not null,
  str                           integer not null,
  dex                           integer not null,
  intelligence                  integer not null,
  luk                           integer not null,
  hp                            integer not null,
  mp                            integer not null,
  watk                          integer not null,
  matk                          integer not null,
  wdef                          integer not null,
  mdef                          integer not null,
  acc                           integer not null,
  avoid                         integer not null,
  hands                         integer not null,
  speed                         integer not null,
  jump                          integer not null,
  vicious_hammer                integer not null,
  item_exp                      integer not null,
  durability                    integer not null,
  enhance                       integer not null,
  potential1                    integer not null,
  potential2                    integer not null,
  potential3                    integer not null,
  hp_r                          integer not null,
  mp_r                          integer not null,
  item_level                    integer not null,
  constraint uq_inventoryequipment_inventory_item_id unique (inventory_item_id),
  constraint pk_inventoryequipment primary key (id)
);

create table inventoryitems (
  id                            integer auto_increment not null,
  character_id                  integer,
  account_id                    integer,
  package_id                    integer,
  item_id                       integer not null,
  inventory_type                integer not null,
  position                      integer not null,
  quantity                      integer not null,
  owner                         varchar(255),
  gm_log                        varchar(255),
  unique_id                     integer not null,
  flag                          integer not null,
  expire_date                   bigint not null,
  type                          integer not null,
  sender                        varchar(255) not null,
  constraint pk_inventoryitems primary key (id)
);

create table inventorylog (
  id                            integer auto_increment not null,
  inventory_item_id             integer not null,
  msg                           varchar(255) not null,
  constraint pk_inventorylog primary key (id)
);

create table inventoryslot (
  id                            integer auto_increment not null,
  character_id                  integer,
  equip                         integer,
  use_                          integer,
  setup                         integer,
  etc                           integer,
  cash                          integer,
  constraint uq_inventoryslot_character_id unique (character_id),
  constraint pk_inventoryslot primary key (id)
);

create table invitecodedata (
  id                            integer auto_increment not null,
  code                          varchar(255) not null,
  user                          varchar(255),
  time                          varchar(255),
  ip                            varchar(255),
  active                        integer not null,
  constraint pk_invitecodedata primary key (id)
);

create table ipvotelog (
  id                            integer auto_increment not null,
  acc_id                        varchar(255) not null,
  ip_address                    varchar(255) not null,
  vote_time                     varchar(255) not null,
  vote_type                     integer not null,
  constraint pk_ipvotelog primary key (id)
);

create table keymap (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  key_                          integer not null,
  type_                         integer not null,
  action                        integer not null,
  constraint pk_keymap primary key (id)
);

create table loginlog (
  account                       varchar(255),
  password                      varchar(255),
  login_type                    varchar(255),
  ip                            varchar(255),
  time                          varchar(255),
  active                        varchar(255)
);

create table macbans (
  id                            integer auto_increment not null,
  mac                           varchar(255) not null,
  constraint pk_macbans primary key (id)
);

create table macfilters (
  id                            integer auto_increment not null,
  filter                        varchar(255) not null,
  constraint pk_macfilters primary key (id)
);

create table monsterbook (
  id                            integer auto_increment not null,
  char_id                       integer not null,
  card_id                       integer not null,
  level                         integer,
  constraint pk_monsterbook primary key (id)
);

create table mountdata (
  id                            integer auto_increment not null,
  character_id                  integer,
  level                         integer not null,
  exp                           integer not null,
  fatigue                       integer not null,
  constraint uq_mountdata_character_id unique (character_id),
  constraint pk_mountdata primary key (id)
);

create table mts_cart (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  item_id                       integer not null,
  constraint pk_mts_cart primary key (id)
);

create table mtsequipment (
  id                            integer auto_increment not null,
  inventory_item_id             integer not null,
  upgrade_slots                 integer not null,
  level                         integer not null,
  str                           integer not null,
  dex                           integer not null,
  int_                          integer not null,
  luk                           integer not null,
  hp                            integer not null,
  mp                            integer not null,
  watk                          integer not null,
  matk                          integer not null,
  wdef                          integer not null,
  mdef                          integer not null,
  acc                           integer not null,
  avoid                         integer not null,
  hands                         integer not null,
  speed                         integer not null,
  jump                          integer not null,
  vicious_hammer                integer not null,
  item_exp                      integer not null,
  durability                    integer not null,
  enhance                       integer not null,
  potential1                    integer not null,
  potential2                    integer not null,
  potential3                    integer not null,
  hp_r                          integer not null,
  mp_r                          integer not null,
  constraint uq_mtsequipment_inventory_item_id unique (inventory_item_id),
  constraint pk_mtsequipment primary key (id)
);

create table mtsitems (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  account_id                    integer,
  package_id                    integer,
  item_id                       integer not null,
  inventory_type                integer not null,
  position                      integer not null,
  quantity                      integer not null,
  owner                         varchar(255),
  gm_log                        varchar(255),
  unique_id                     integer not null,
  flag                          integer not null,
  expire_date                   datetime(6) not null,
  type                          integer not null,
  sender                        varchar(255) not null,
  constraint pk_mtsitems primary key (id)
);

create table mts_items (
  id                            integer auto_increment not null,
  tab                           integer not null,
  price                         integer not null,
  character_id                  integer not null,
  seller                        varchar(255) not null,
  expiration                    datetime(6) not null,
  constraint pk_mts_items primary key (id)
);

create table mtstransfer (
  id                            integer auto_increment not null,
  character_id                  integer,
  account_id                    integer,
  package_id                    integer,
  item_id                       integer not null,
  inventory_type                integer not null,
  position                      integer not null,
  quantity                      integer not null,
  owner                         varchar(255),
  gm_log                        varchar(255),
  unique_id                     integer not null,
  flag                          integer not null,
  expire_date                   datetime(6) not null,
  type                          integer not null,
  sender                        varchar(255) not null,
  constraint pk_mtstransfer primary key (id)
);

create table mtstransferequipment (
  id                            integer auto_increment not null,
  inventory_item_id             integer not null,
  upgrade_slots                 integer not null,
  level                         integer not null,
  str                           integer not null,
  dex                           integer not null,
  int_                          integer not null,
  luk                           integer not null,
  hp                            integer not null,
  mp                            integer not null,
  watk                          integer not null,
  matk                          integer not null,
  wdef                          integer not null,
  mdef                          integer not null,
  acc                           integer not null,
  avoid                         integer not null,
  hands                         integer not null,
  speed                         integer not null,
  jump                          integer not null,
  vicious_hammer                integer not null,
  item_exp                      integer not null,
  durability                    integer not null,
  enhance                       integer not null,
  potential1                    integer not null,
  potential2                    integer not null,
  potential3                    integer not null,
  hp_r                          integer not null,
  mp_r                          integer not null,
  constraint uq_mtstransferequipment_inventory_item_id unique (inventory_item_id),
  constraint pk_mtstransferequipment primary key (id)
);

create table mulungdojo (
  id                            integer auto_increment not null,
  char_id                       integer not null,
  stage                         integer not null,
  constraint pk_mulungdojo primary key (id)
);

create table notes (
  id                            integer auto_increment not null,
  to_                           varchar(255) not null,
  from_                         varchar(255) not null,
  message                       varchar(255) not null,
  timestamp                     bigint not null,
  gift                          integer not null,
  constraint pk_notes primary key (id)
);

create table nxcode (
  code                          varchar(255) not null,
  valid                         integer not null,
  user                          varchar(255),
  type                          integer not null,
  item                          integer not null,
  size                          integer,
  constraint pk_nxcode primary key (code)
);

create table onetimelog (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  log                           varchar(255) not null,
  constraint pk_onetimelog primary key (id)
);

create table pets (
  id                            integer auto_increment not null,
  name                          varchar(255),
  level                         integer not null,
  closeness                     integer not null,
  fullness                      integer not null,
  seconds                       integer not null,
  flags                         integer not null,
  constraint pk_pets primary key (id)
);

create table playernpcs (
  id                            integer auto_increment not null,
  name                          varchar(255) not null,
  hair                          integer not null,
  face                          integer not null,
  skin                          integer not null,
  x                             integer not null,
  y                             integer not null,
  map                           integer not null,
  char_id                       integer not null,
  script_id                     integer not null,
  foothold                      integer not null,
  dir                           integer not null,
  gender                        integer not null,
  pets                          varchar(255),
  constraint pk_playernpcs primary key (id)
);

create table playernpcs_equip (
  id                            integer auto_increment not null,
  npc_id                        integer not null,
  equip_id                      integer not null,
  equip_pos                     integer not null,
  char_id                       integer not null,
  constraint pk_playernpcs_equip primary key (id)
);

create table prizelog (
  id                            integer auto_increment not null,
  accid                         integer not null,
  boss_id                       varchar(255) not null,
  constraint pk_prizelog primary key (id)
);

create table questactions (
  id                            integer auto_increment not null,
  quest_id                      integer not null,
  status                        integer not null,
  data                          varbinary(255) not null,
  constraint pk_questactions primary key (id)
);

create table questinfo (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  quest                         integer not null,
  custom_data                   varchar(255),
  constraint pk_questinfo primary key (id)
);

create table questmonster (
  id                            integer auto_increment not null,
  quest_id                      integer,
  monster_id                    integer,
  zt                            integer not null,
  char_id                       integer,
  name                          varchar(255),
  constraint pk_questmonster primary key (id)
);

create table questnpc (
  id                            integer auto_increment not null,
  npcid                         integer,
  item_id                       integer,
  sl                            integer,
  zt                            integer,
  name                          varchar(255),
  item                          integer,
  item_sl                       integer,
  money                         integer,
  constraint pk_questnpc primary key (id)
);

create table questrequirements (
  id                            integer auto_increment not null,
  quest_id                      integer not null,
  status                        integer not null,
  data                          varbinary(255) not null,
  constraint pk_questrequirements primary key (id)
);

create table queststatus (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  quest                         integer not null,
  status                        integer not null,
  time                          integer not null,
  forfeited                     integer not null,
  custom_data                   varchar(255),
  constraint pk_queststatus primary key (id)
);

create table queststatusmobs (
  id                            integer auto_increment not null,
  quest_status_id               integer not null,
  mob                           integer not null,
  count                         integer not null,
  constraint pk_queststatusmobs primary key (id)
);

create table reactordrops (
  id                            integer auto_increment not null,
  reactor_id                    integer not null,
  item_id                       integer not null,
  chance                        integer not null,
  quest_id                      integer not null,
  constraint pk_reactordrops primary key (id)
);

create table regrocklocations (
  id                            integer auto_increment not null,
  character_id                  integer,
  map_id                        integer,
  constraint pk_regrocklocations primary key (id)
);

create table reports (
  id                            integer auto_increment not null,
  report_time                   datetime(6) not null,
  reporter_id                   integer not null,
  victimid                      integer not null,
  reason                        integer not null,
  chat_log                      varchar(255) not null,
  status                        varchar(255) not null,
  constraint pk_reports primary key (id)
);

create table rings (
  id                            integer auto_increment not null,
  partner_ring_id               integer not null,
  partner_chr_id                integer not null,
  item_id                       integer not null,
  partner_name                  varchar(255) not null,
  constraint pk_rings primary key (id)
);

create table savedlocations (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  location_type                 integer not null,
  map                           integer not null,
  constraint uq_savedlocations_character_id unique (character_id),
  constraint pk_savedlocations primary key (id)
);

create table shops (
  id                            integer auto_increment not null,
  npc_id                        integer not null,
  constraint pk_shops primary key (id)
);

create table shopitems (
  id                            integer auto_increment not null,
  shop_id                       integer not null,
  item_id                       integer not null,
  price                         integer not null,
  pitch                         integer not null,
  position                      integer not null,
  req_item                      integer,
  req_item_q                    integer,
  constraint pk_shopitems primary key (id)
);

create table skills (
  id                            integer auto_increment not null,
  skill_id                      integer not null,
  character_id                  integer not null,
  skill_level                   integer not null,
  master_level                  integer not null,
  expiration                    bigint not null,
  constraint pk_skills primary key (id)
);

create table skills_cooldowns (
  id                            integer auto_increment not null,
  char_id                       integer not null,
  skill_id                      integer not null,
  length                        bigint not null,
  start_time                    bigint not null,
  constraint pk_skills_cooldowns primary key (id)
);

create table skillmacros (
  id                            integer auto_increment not null,
  character_id                  integer not null,
  position                      integer not null,
  skill1                        integer not null,
  skill2                        integer not null,
  skill3                        integer not null,
  name                          varchar(255),
  shout                         integer not null,
  constraint pk_skillmacros primary key (id)
);

create table speedruns (
  id                            integer auto_increment not null,
  type                          varchar(255) not null,
  leader                        varchar(255) not null,
  time_string                   varchar(255) not null,
  time                          bigint not null,
  members                       varchar(255) not null,
  constraint pk_speedruns primary key (id)
);

create table storages (
  id                            integer auto_increment not null,
  account_id                    integer,
  slots                         integer,
  meso                          integer,
  constraint uq_storages_account_id unique (account_id),
  constraint pk_storages primary key (id)
);

create table trocklocations (
  id                            integer auto_increment not null,
  character_id                  integer,
  map_id                        integer,
  constraint pk_trocklocations primary key (id)
);

create table uselog (
  account                       varchar(255),
  ip                            varchar(255),
  time                          varchar(255),
  use_type                      varchar(255),
  active                        varchar(255),
  new_password                  varchar(255),
  old_password                  varchar(255)
);

create table wishlist (
  character_id                  integer not null,
  sn                            integer not null
);

create table wz_customlife (
  id                            integer auto_increment not null,
  data_id                       integer not null,
  f                             integer not null,
  hide                          integer not null,
  fh                            integer not null,
  type                          varchar(255) not null,
  cy                            integer not null,
  rx0                           integer not null,
  rx1                           integer not null,
  x                             integer not null,
  y                             integer not null,
  mob_time                      integer,
  mid                           integer not null,
  constraint pk_wz_customlife primary key (id)
);

create table wz_itemadddata (
  id                            integer auto_increment not null,
  item_id                       integer not null,
  key                           varchar(255) not null,
  sub_key                       varchar(255) not null,
  value                         varchar(255) not null,
  constraint pk_wz_itemadddata primary key (id)
);

create table wz_itemdata (
  id                            integer auto_increment not null,
  name                          varchar(255),
  msg                           varchar(255),
  desc                          varchar(255),
  slot_max                      integer not null,
  price                         varchar(255) not null,
  whole_price                   integer not null,
  state_change                  integer not null,
  flags                         integer not null,
  karma                         integer not null,
  meso                          integer not null,
  monster_book                  integer not null,
  item_make_level               integer not null,
  quest_id                      integer not null,
  scroll_reqs                   varchar(255),
  consume_item                  varchar(255),
  total_prob                    integer not null,
  inc_skill                     varchar(255) not null,
  replace_id                    integer not null,
  replace_msg                   varchar(255) not null,
  create                        integer not null,
  after_image                   varchar(255) not null,
  constraint pk_wz_itemdata primary key (id)
);

create table wz_itemequipdata (
  id                            integer auto_increment not null,
  item_id                       integer not null,
  item_level                    integer not null,
  key                           varchar(255) not null,
  value                         integer not null,
  constraint pk_wz_itemequipdata primary key (id)
);

create table wz_itemrewarddata (
  id                            integer auto_increment not null,
  item_id                       integer not null,
  item                          integer not null,
  prob                          integer not null,
  quantity                      integer not null,
  period                        integer not null,
  world_msg                     varchar(255) not null,
  effect                        varchar(255) not null,
  constraint pk_wz_itemrewarddata primary key (id)
);

create table wz_mobskilldata (
  id                            integer auto_increment not null,
  skill_id                      integer not null,
  level                         integer not null,
  hp                            integer not null,
  mp_con                        integer not null,
  x                             integer not null,
  y                             integer not null,
  time                          integer not null,
  prop                          integer not null,
  limit_                        integer not null,
  spawn_effect                  integer not null,
  interval_                     integer not null,
  summons                       varchar(255) not null,
  ltx                           integer not null,
  lty                           integer not null,
  rbx                           integer not null,
  rby                           integer not null,
  once                          integer not null,
  constraint pk_wz_mobskilldata primary key (id)
);

create table wz_npcnamedata (
  id                            integer auto_increment not null,
  npc                           integer not null,
  name                          varchar(255) not null,
  constraint pk_wz_npcnamedata primary key (id)
);

create table wz_oxdata (
  question_set                  integer not null,
  question_id                   integer not null,
  question                      varchar(255) not null,
  display                       varchar(255) not null,
  answer                        varchar(255) not null,
  constraint pk_wz_oxdata primary key (question_set,question_id)
);

create table wz_questactdata (
  id                            integer auto_increment not null,
  quest_id                      integer not null,
  name                          varchar(255) not null,
  type                          integer not null,
  int_store                     integer not null,
  applicable_jobs               varchar(255) not null,
  unique_id                     integer not null,
  constraint pk_wz_questactdata primary key (id)
);

create table wz_questactitemdata (
  id                            integer auto_increment not null,
  item_id                       integer not null,
  count                         integer not null,
  period                        integer not null,
  gender                        integer not null,
  job                           integer not null,
  job_ex                        integer not null,
  prop                          integer not null,
  unique_id                     integer not null,
  constraint pk_wz_questactitemdata primary key (id)
);

create table wz_questactquestdata (
  id                            integer auto_increment not null,
  quest                         integer not null,
  state                         integer not null,
  unique_id                     integer not null,
  constraint pk_wz_questactquestdata primary key (id)
);

create table wz_questactskilldata (
  id                            integer auto_increment not null,
  skill_id                      integer not null,
  skill_level                   integer not null,
  master_level                  integer not null,
  unique_id                     integer not null,
  constraint pk_wz_questactskilldata primary key (id)
);

create table wz_questdata (
  id                            integer auto_increment not null,
  name                          varchar(255) not null,
  auto_start                    tinyint(1) not null,
  auto_pre_complete             tinyint(1) not null,
  view_medal_item               integer not null,
  selected_skill_id             integer not null,
  blocked                       tinyint(1) not null,
  auto_accept                   tinyint(1) not null,
  auto_complete                 tinyint(1) not null,
  constraint pk_wz_questdata primary key (id)
);

create table wz_questpartydata (
  id                            integer auto_increment not null,
  quest_id                      integer not null,
  rank                          varchar(255) not null,
  mode                          varchar(255) not null,
  property                      varchar(255) not null,
  value                         integer not null,
  constraint pk_wz_questpartydata primary key (id)
);

create table wz_questreqdata (
  id                            integer auto_increment not null,
  quest_id                      integer not null,
  name                          varchar(255) not null,
  type                          integer not null,
  string_store                  varchar(255) not null,
  int_stores_first              varchar(255) not null,
  int_stores_second             varchar(255) not null,
  constraint pk_wz_questreqdata primary key (id)
);

create table zaksquads (
  id                            integer auto_increment not null,
  channel                       integer not null,
  leader_id                     integer not null,
  status                        integer not null,
  members                       integer not null,
  constraint pk_zaksquads primary key (id)
);

alter table aclog add constraint fk_aclog_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table accounts_info add constraint fk_accounts_info_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table achievements add constraint fk_achievements_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table alliances add constraint fk_alliances_leader_id foreign key (leader_id) references characters (id) on delete restrict on update restrict;

alter table alliances add constraint fk_alliances_guild1 foreign key (guild1) references guilds (id) on delete restrict on update restrict;

alter table alliances add constraint fk_alliances_guild2 foreign key (guild2) references guilds (id) on delete restrict on update restrict;

alter table alliances add constraint fk_alliances_guild3 foreign key (guild3) references guilds (id) on delete restrict on update restrict;

alter table alliances add constraint fk_alliances_guild4 foreign key (guild4) references guilds (id) on delete restrict on update restrict;

alter table alliances add constraint fk_alliances_guild5 foreign key (guild5) references guilds (id) on delete restrict on update restrict;

create index ix_bbs_replies_thread_id on bbs_replies (thread_id);
alter table bbs_replies add constraint fk_bbs_replies_thread_id foreign key (thread_id) references bbs_threads (id) on delete restrict on update restrict;

create index ix_bbs_replies_poster_id on bbs_replies (poster_id);
alter table bbs_replies add constraint fk_bbs_replies_poster_id foreign key (poster_id) references characters (id) on delete restrict on update restrict;

create index ix_bbs_replies_guild_id on bbs_replies (guild_id);
alter table bbs_replies add constraint fk_bbs_replies_guild_id foreign key (guild_id) references guilds (id) on delete restrict on update restrict;

create index ix_bbs_threads_poster_id on bbs_threads (poster_id);
alter table bbs_threads add constraint fk_bbs_threads_poster_id foreign key (poster_id) references characters (id) on delete restrict on update restrict;

create index ix_bbs_threads_guild_id on bbs_threads (guild_id);
alter table bbs_threads add constraint fk_bbs_threads_guild_id foreign key (guild_id) references guilds (id) on delete restrict on update restrict;

create index ix_bosslog_character_id on bosslog (character_id);
alter table bosslog add constraint fk_bosslog_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_characters_account_id on characters (account_id);
alter table characters add constraint fk_characters_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

create index ix_character_slots_acc_id on character_slots (acc_id);
alter table character_slots add constraint fk_character_slots_acc_id foreign key (acc_id) references accounts (id) on delete restrict on update restrict;

create index ix_cheatlog_character_id on cheatlog (character_id);
alter table cheatlog add constraint fk_cheatlog_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

alter table csequipment add constraint fk_csequipment_inventory_item_id foreign key (inventory_item_id) references csitems (id) on delete restrict on update restrict;

create index ix_csitems_character_id on csitems (character_id);
alter table csitems add constraint fk_csitems_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_csitems_account_id on csitems (account_id);
alter table csitems add constraint fk_csitems_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table dueyequipment add constraint fk_dueyequipment_inventoryitemid foreign key (inventoryitemid) references dueyitems (id) on delete restrict on update restrict;

create index ix_dueyitems_character_id on dueyitems (character_id);
alter table dueyitems add constraint fk_dueyitems_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_dueyitems_account_id on dueyitems (account_id);
alter table dueyitems add constraint fk_dueyitems_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table dueyitems add constraint fk_dueyitems_package_id foreign key (package_id) references dueypackages (id) on delete restrict on update restrict;

alter table famelog add constraint fk_famelog_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_famelog_characterid_to on famelog (characterid_to);
alter table famelog add constraint fk_famelog_characterid_to foreign key (characterid_to) references characters (id) on delete restrict on update restrict;

alter table families add constraint fk_families_leader_id foreign key (leader_id) references characters (id) on delete restrict on update restrict;

create index ix_game_poll_reply_account_id on game_poll_reply (account_id);
alter table game_poll_reply add constraint fk_game_poll_reply_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table guilds add constraint fk_guilds_alliance foreign key (alliance) references alliances (id) on delete restrict on update restrict;

create index ix_hiredmerch_character_id on hiredmerch (character_id);
alter table hiredmerch add constraint fk_hiredmerch_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_hiredmerch_account_id on hiredmerch (account_id);
alter table hiredmerch add constraint fk_hiredmerch_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table hiredmerchequipment add constraint fk_hiredmerchequipment_inventory_item_id foreign key (inventory_item_id) references hiredmerchitems (id) on delete restrict on update restrict;

create index ix_hiredmerchitems_character_id on hiredmerchitems (character_id);
alter table hiredmerchitems add constraint fk_hiredmerchitems_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_hiredmerchitems_account_id on hiredmerchitems (account_id);
alter table hiredmerchitems add constraint fk_hiredmerchitems_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table inventoryequipment add constraint fk_inventoryequipment_inventory_item_id foreign key (inventory_item_id) references inventoryitems (id) on delete restrict on update restrict;

create index ix_inventoryitems_character_id on inventoryitems (character_id);
alter table inventoryitems add constraint fk_inventoryitems_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_inventoryitems_account_id on inventoryitems (account_id);
alter table inventoryitems add constraint fk_inventoryitems_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table inventoryslot add constraint fk_inventoryslot_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_keymap_character_id on keymap (character_id);
alter table keymap add constraint fk_keymap_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_monsterbook_char_id on monsterbook (char_id);
alter table monsterbook add constraint fk_monsterbook_char_id foreign key (char_id) references characters (id) on delete restrict on update restrict;

alter table mountdata add constraint fk_mountdata_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

alter table mtsequipment add constraint fk_mtsequipment_inventory_item_id foreign key (inventory_item_id) references mtsitems (id) on delete restrict on update restrict;

create index ix_mtsitems_character_id on mtsitems (character_id);
alter table mtsitems add constraint fk_mtsitems_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_mtsitems_account_id on mtsitems (account_id);
alter table mtsitems add constraint fk_mtsitems_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

create index ix_mts_items_character_id on mts_items (character_id);
alter table mts_items add constraint fk_mts_items_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_mtstransfer_character_id on mtstransfer (character_id);
alter table mtstransfer add constraint fk_mtstransfer_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_mtstransfer_account_id on mtstransfer (account_id);
alter table mtstransfer add constraint fk_mtstransfer_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

alter table mtstransferequipment add constraint fk_mtstransferequipment_inventory_item_id foreign key (inventory_item_id) references inventoryitems (id) on delete restrict on update restrict;

create index ix_onetimelog_character_id on onetimelog (character_id);
alter table onetimelog add constraint fk_onetimelog_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_questinfo_character_id on questinfo (character_id);
alter table questinfo add constraint fk_questinfo_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_queststatus_character_id on queststatus (character_id);
alter table queststatus add constraint fk_queststatus_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_queststatusmobs_quest_status_id on queststatusmobs (quest_status_id);
alter table queststatusmobs add constraint fk_queststatusmobs_quest_status_id foreign key (quest_status_id) references queststatus (id) on delete restrict on update restrict;

create index ix_regrocklocations_character_id on regrocklocations (character_id);
alter table regrocklocations add constraint fk_regrocklocations_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

alter table savedlocations add constraint fk_savedlocations_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_skills_character_id on skills (character_id);
alter table skills add constraint fk_skills_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_skills_cooldowns_char_id on skills_cooldowns (char_id);
alter table skills_cooldowns add constraint fk_skills_cooldowns_char_id foreign key (char_id) references characters (id) on delete restrict on update restrict;

create index ix_skillmacros_character_id on skillmacros (character_id);
alter table skillmacros add constraint fk_skillmacros_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

alter table storages add constraint fk_storages_account_id foreign key (account_id) references accounts (id) on delete restrict on update restrict;

create index ix_trocklocations_character_id on trocklocations (character_id);
alter table trocklocations add constraint fk_trocklocations_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_wishlist_character_id on wishlist (character_id);
alter table wishlist add constraint fk_wishlist_character_id foreign key (character_id) references characters (id) on delete restrict on update restrict;

create index ix_wz_itemadddata_item_id on wz_itemadddata (item_id);
alter table wz_itemadddata add constraint fk_wz_itemadddata_item_id foreign key (item_id) references wz_itemdata (id) on delete restrict on update restrict;

create index ix_wz_itemequipdata_item_id on wz_itemequipdata (item_id);
alter table wz_itemequipdata add constraint fk_wz_itemequipdata_item_id foreign key (item_id) references wz_itemdata (id) on delete restrict on update restrict;

create index ix_wz_itemrewarddata_item_id on wz_itemrewarddata (item_id);
alter table wz_itemrewarddata add constraint fk_wz_itemrewarddata_item_id foreign key (item_id) references wz_itemdata (id) on delete restrict on update restrict;

create index ix_wz_questactdata_quest_id on wz_questactdata (quest_id);
alter table wz_questactdata add constraint fk_wz_questactdata_quest_id foreign key (quest_id) references wz_questdata (id) on delete restrict on update restrict;

create index ix_wz_questpartydata_quest_id on wz_questpartydata (quest_id);
alter table wz_questpartydata add constraint fk_wz_questpartydata_quest_id foreign key (quest_id) references wz_questdata (id) on delete restrict on update restrict;

create index ix_wz_questreqdata_quest_id on wz_questreqdata (quest_id);
alter table wz_questreqdata add constraint fk_wz_questreqdata_quest_id foreign key (quest_id) references wz_questdata (id) on delete restrict on update restrict;

