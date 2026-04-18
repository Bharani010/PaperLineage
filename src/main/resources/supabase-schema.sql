-- Run this once in the Supabase SQL editor before Phase 5

create extension if not exists vector;

create table if not exists embeddings (
    id          bigserial primary key,
    source      text        not null,
    chunk_text  text        not null,
    embedding   vector(384) not null,
    created_at  timestamptz default now()
);

create index if not exists embeddings_embedding_idx
    on embeddings using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);

create or replace function match_embeddings(
    query_embedding vector(384),
    match_count     int default 5
)
returns table (
    id          bigint,
    source      text,
    chunk_text  text,
    similarity  float
)
language sql stable
as $$
    select
        id,
        source,
        chunk_text,
        1 - (embedding <=> query_embedding) as similarity
    from embeddings
    order by embedding <=> query_embedding
    limit match_count;
$$;
