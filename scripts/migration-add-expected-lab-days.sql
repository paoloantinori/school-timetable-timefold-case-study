-- Migration: Add expected_lab_days_per_week column to course table
-- Date: 2026-03-08
-- Task: TASK-201
--
-- This adds a persistent field to store the pre-computed expected number of
-- days per week for lab courses, based on the actual LabRotation period duration.
--
-- Apply this migration to the database before running the rebuilt application.

-- Add the column with default value of 1
ALTER TABLE course ADD COLUMN expected_lab_days_per_week INT DEFAULT 1;

-- Initialize the field for existing lab courses using current 38-week logic
UPDATE course
SET expected_lab_days_per_week = GREATEST(1,
    ROUND(CEIL(totalhours::numeric / NULLIF(labblocksize, 0)) / 38.0))
WHERE islab = true;

-- Verify the update
SELECT
    subject,
    studentgroup_id,
    totalhours,
    labblocksize,
    expected_lab_days_per_week,
    CEIL(totalhours::numeric / NULLIF(labblocksize, 0)) AS block_count
FROM course
WHERE islab = true
ORDER BY expected_lab_days_per_week DESC, block_count DESC
LIMIT 20;

-- Expected results:
-- - High block count courses (Lavorazioni meccaniche, Confezione): ~2 days
-- - Most other lab courses: ~1 day
