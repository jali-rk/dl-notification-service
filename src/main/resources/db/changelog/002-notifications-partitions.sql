-- liquibase formatted sql
-- changeset copilot:002-01-create-monthly-partitions splitStatements:false endDelimiter:END_PARTITIONS

-- Helper function to create monthly partitions with 8 hash subpartitions by user_id
DO $BODY$
DECLARE
  start_date DATE := DATE '2025-01-01';
  end_date   DATE := DATE '2026-12-01';
  part_start DATE;
  part_end   DATE;
  suffix     TEXT;
  i          INT;
BEGIN
  part_start := start_date;
  WHILE part_start < end_date LOOP
    part_end := (part_start + INTERVAL '1 month')::DATE;
    suffix := to_char(part_start, 'YYYY_MM');

    EXECUTE format(
      'CREATE TABLE IF NOT EXISTS notifications_%s PARTITION OF notifications FOR VALUES FROM (%L) TO (%L) PARTITION BY HASH (user_id)'
      , suffix, part_start, part_end
    );

    FOR i IN 0..7 LOOP
      EXECUTE format(
        'CREATE TABLE IF NOT EXISTS notifications_%s_h%s PARTITION OF notifications_%s FOR VALUES WITH (MODULUS 8, REMAINDER %s)'
        , suffix, i, suffix, i
      );

      -- indexes per subpartition
      EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_n_%s_h%s_user_channel ON notifications_%s_h%s (user_id, channel, created_at DESC)'
        , suffix, i, suffix, i
      );
      EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_n_%s_h%s_user_isread ON notifications_%s_h%s (user_id, is_read, created_at DESC)'
        , suffix, i, suffix, i
      );
      EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_n_%s_h%s_created_at ON notifications_%s_h%s (created_at)'
        , suffix, i, suffix, i
      );
    END LOOP;

    part_start := (part_start + INTERVAL '1 month')::DATE;
  END LOOP;
END $BODY$;
END_PARTITIONS

-- changeset copilot:002-02-triggers-updated-at splitStatements:false endDelimiter:END_TRIGGERS
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $BODY$
BEGIN
  NEW.updated_at := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$BODY$ LANGUAGE plpgsql;

DO $BODY$
DECLARE
  r RECORD;
BEGIN
  FOR r IN SELECT relname FROM pg_class WHERE relname LIKE 'notifications_%_h%' AND relkind = 'r'
  LOOP
    EXECUTE format('CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON %s FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()', r.relname, r.relname);
  END LOOP;
END $BODY$;
END_TRIGGERS
