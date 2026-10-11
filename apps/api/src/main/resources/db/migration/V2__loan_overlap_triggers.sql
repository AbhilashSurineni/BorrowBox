-- Business rule 3 (no overlapping bookings) — defence in depth.
-- MySQL has no exclusion constraints. The primary guard is LoanService, which locks the
-- item row (SELECT ... FOR UPDATE) and checks for overlaps inside one transaction.
-- These triggers reject any write that would still produce an overlap between two
-- APPROVED/ACTIVE/OVERDUE loans of the same item (SIGNAL SQLSTATE '45000',
-- mapped by the API to 409 DATES_UNAVAILABLE). See docs/DB_NOTES.md.

CREATE TRIGGER loans_no_overlap_insert
BEFORE INSERT ON loans
FOR EACH ROW
BEGIN
  IF NEW.status IN ('APPROVED', 'ACTIVE', 'OVERDUE') AND EXISTS (
    SELECT 1 FROM loans l
    WHERE l.item_id = NEW.item_id
      AND l.id <> NEW.id
      AND l.status IN ('APPROVED', 'ACTIVE', 'OVERDUE')
      AND l.start_date <= NEW.end_date
      AND l.end_date >= NEW.start_date
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'loans_no_overlap';
  END IF;
END;

CREATE TRIGGER loans_no_overlap_update
BEFORE UPDATE ON loans
FOR EACH ROW
BEGIN
  IF NEW.status IN ('APPROVED', 'ACTIVE', 'OVERDUE') AND EXISTS (
    SELECT 1 FROM loans l
    WHERE l.item_id = NEW.item_id
      AND l.id <> NEW.id
      AND l.status IN ('APPROVED', 'ACTIVE', 'OVERDUE')
      AND l.start_date <= NEW.end_date
      AND l.end_date >= NEW.start_date
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'loans_no_overlap';
  END IF;
END;
