-- Compatibility entrypoint for psql. The versioned baseline is the source of truth.
\set ON_ERROR_STOP on
\ir migrations/V001__initial_schema.sql
