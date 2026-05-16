create table if not exists folders (
  id text primary key,
  parent_id text,
  name text not null,
  order_index integer not null default 0,
  is_favourite boolean not null default false,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create table if not exists notes (
  id text primary key,
  folder_id text,
  title text not null,
  body_plain_text text not null default '',
  is_pinned boolean not null default false,
  is_favourite boolean not null default false,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create table if not exists blocks (
  id text primary key,
  note_id text not null,
  type text not null,
  content text not null default '',
  order_index integer not null default 0
);

create table if not exists tags (
  name text primary key
);

create table if not exists note_tags (
  note_id text not null,
  tag_name text not null,
  primary key (note_id, tag_name)
);

create table if not exists attachments (
  id text primary key,
  note_id text not null,
  file_name text not null,
  mime_type text not null,
  size_bytes bigint not null,
  local_path text not null,
  remote_url text,
  created_at bigint not null,
  deleted_at bigint
);

create table if not exists note_tables (
  id text primary key,
  note_id text not null,
  row_count integer not null,
  column_count integer not null,
  cells_json text not null,
  order_index integer not null default 0,
  created_at bigint not null,
  updated_at bigint not null
);
