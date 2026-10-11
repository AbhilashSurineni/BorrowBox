-- BorrowBox schema for MySQL 8 (InnoDB, utf8mb4). Ported from the PostgreSQL/Prisma schema.
-- UUIDs are CHAR(36) generated in Java; timestamps are DATETIME(6) in UTC; enums are VARCHAR + CHECK.

CREATE TABLE users (
  id                 CHAR(36)      NOT NULL,
  name               VARCHAR(100)  NOT NULL,
  email              VARCHAR(150)  NOT NULL,
  password_hash      VARCHAR(255)  NOT NULL,
  role               VARCHAR(10)   NOT NULL DEFAULT 'MEMBER',
  status             VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
  department         VARCHAR(100)  NOT NULL,
  phone              VARCHAR(20)   NULL,
  avatar_url         TEXT          NULL,
  contact_preference VARCHAR(10)   NOT NULL DEFAULT 'EMAIL',
  email_verified     TINYINT(1)    NOT NULL DEFAULT 0,
  rating_avg         DECIMAL(3,2)  NOT NULL DEFAULT 0,
  rating_count       INT           NOT NULL DEFAULT 0,
  warning_count      INT           NOT NULL DEFAULT 0,
  deleted_at         DATETIME(6)   NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY users_email_key (email),
  KEY users_status_idx (status),
  CONSTRAINT users_role_check CHECK (role IN ('MEMBER', 'ADMIN')),
  CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
  CONSTRAINT users_contact_preference_check CHECK (contact_preference IN ('EMAIL', 'PHONE', 'BOTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
  id          CHAR(36)    NOT NULL,
  user_id     CHAR(36)    NOT NULL,
  family_id   CHAR(36)    NOT NULL,
  token_hash  CHAR(64)    NOT NULL,
  expires_at  DATETIME(6) NOT NULL,
  revoked_at  DATETIME(6) NULL,
  replaced_by CHAR(36)    NULL,
  created_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY refresh_tokens_token_hash_key (token_hash),
  KEY refresh_tokens_user_id_idx (user_id),
  KEY refresh_tokens_family_id_idx (family_id),
  CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_tokens (
  id         CHAR(36)    NOT NULL,
  user_id    CHAR(36)    NOT NULL,
  type       VARCHAR(20) NOT NULL,
  token_hash CHAR(64)    NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at    DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY auth_tokens_token_hash_key (token_hash),
  KEY auth_tokens_user_id_type_idx (user_id, type),
  CONSTRAINT auth_tokens_type_check CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
  CONSTRAINT auth_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE categories (
  id         CHAR(36)    NOT NULL,
  name       VARCHAR(60) NOT NULL,
  icon       VARCHAR(60) NULL,
  is_active  TINYINT(1)  NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY categories_name_key (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE items (
  id              CHAR(36)     NOT NULL,
  owner_id        CHAR(36)     NOT NULL,
  category_id     CHAR(36)     NOT NULL,
  name            VARCHAR(150) NOT NULL,
  description     TEXT         NOT NULL,
  item_condition  VARCHAR(4)   NOT NULL,
  max_loan_days   INT          NOT NULL DEFAULT 14,
  pickup_location VARCHAR(200) NOT NULL,
  deposit_note    VARCHAR(500) NULL,
  is_paused       TINYINT(1)   NOT NULL DEFAULT 0,
  is_removed      TINYINT(1)   NOT NULL DEFAULT 0,
  removed_reason  TEXT         NULL,
  rating_avg      DECIMAL(3,2) NOT NULL DEFAULT 0,
  rating_count    INT          NOT NULL DEFAULT 0,
  deleted_at      DATETIME(6)  NULL,
  created_at      DATETIME(6)  NOT NULL,
  updated_at      DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY items_category_id_idx (category_id),
  KEY items_owner_id_idx (owner_id),
  KEY items_created_at_idx (created_at),
  FULLTEXT KEY items_search_idx (name, description),
  CONSTRAINT items_condition_check CHECK (item_condition IN ('NEW', 'GOOD', 'FAIR', 'WORN')),
  CONSTRAINT items_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users (id),
  CONSTRAINT items_category_id_fkey FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE item_images (
  id         CHAR(36)    NOT NULL,
  item_id    CHAR(36)    NOT NULL,
  url        TEXT        NOT NULL,
  position   INT         NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY item_images_item_id_idx (item_id),
  CONSTRAINT item_images_item_id_fkey FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE loans (
  id                        CHAR(36)    NOT NULL,
  item_id                   CHAR(36)    NOT NULL,
  borrower_id               CHAR(36)    NOT NULL,
  status                    VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  start_date                DATE        NOT NULL,
  end_date                  DATE        NOT NULL,
  original_end_date         DATE        NOT NULL,
  message                   TEXT        NULL,
  rejection_reason          TEXT        NULL,
  cancellation_reason       TEXT        NULL,
  cancelled_by_id           CHAR(36)    NULL,
  approved_at               DATETIME(6) NULL,
  -- two-step handover: owner marks, borrower confirms
  handover_marked_at        DATETIME(6) NULL,
  handed_over_at            DATETIME(6) NULL,
  -- two-step return: either party marks, the other confirms
  return_marked_by_id       CHAR(36)    NULL,
  return_marked_at          DATETIME(6) NULL,
  returned_at               DATETIME(6) NULL,
  return_condition_note     TEXT        NULL,
  extension_status          VARCHAR(10) NOT NULL DEFAULT 'NONE',
  extension_requested_until DATE        NULL,
  extension_message         TEXT        NULL,
  completed_at              DATETIME(6) NULL,
  version                   BIGINT      NOT NULL DEFAULT 0,
  created_at                DATETIME(6) NOT NULL,
  updated_at                DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY loans_item_id_status_idx (item_id, status),
  KEY loans_borrower_id_status_idx (borrower_id, status),
  KEY loans_status_end_date_idx (status, end_date),
  KEY loans_status_created_at_idx (status, created_at),
  CONSTRAINT loans_dates_check CHECK (end_date >= start_date),
  CONSTRAINT loans_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'ACTIVE', 'OVERDUE', 'RETURNED', 'COMPLETED')),
  CONSTRAINT loans_extension_status_check CHECK (extension_status IN ('NONE', 'REQUESTED', 'APPROVED', 'DECLINED')),
  CONSTRAINT loans_item_id_fkey FOREIGN KEY (item_id) REFERENCES items (id),
  CONSTRAINT loans_borrower_id_fkey FOREIGN KEY (borrower_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reviews (
  id          CHAR(36)    NOT NULL,
  loan_id     CHAR(36)    NOT NULL,
  reviewer_id CHAR(36)    NOT NULL,
  reviewee_id CHAR(36)    NOT NULL,
  rating      SMALLINT    NOT NULL,
  comment     TEXT        NULL,
  created_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY reviews_loan_id_reviewer_id_key (loan_id, reviewer_id),
  KEY reviews_reviewee_id_idx (reviewee_id),
  CONSTRAINT reviews_rating_check CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT reviews_loan_id_fkey FOREIGN KEY (loan_id) REFERENCES loans (id),
  CONSTRAINT reviews_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES users (id),
  CONSTRAINT reviews_reviewee_id_fkey FOREIGN KEY (reviewee_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
  id         CHAR(36)     NOT NULL,
  user_id    CHAR(36)     NOT NULL,
  type       VARCHAR(40)  NOT NULL,
  title      VARCHAR(200) NOT NULL,
  body       TEXT         NOT NULL,
  link       TEXT         NULL,
  is_read    TINYINT(1)   NOT NULL DEFAULT 0,
  -- idempotency key for scheduled notifications, e.g. "due-tomorrow:<loanId>:<date>"
  dedupe_key VARCHAR(191) NULL,
  created_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY notifications_dedupe_key_key (dedupe_key),
  KEY notifications_user_id_is_read_created_at_idx (user_id, is_read, created_at),
  CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reports (
  id             CHAR(36)    NOT NULL,
  reporter_id    CHAR(36)    NOT NULL,
  target_type    VARCHAR(4)  NOT NULL,
  target_id      CHAR(36)    NOT NULL,
  -- user accountable for the report: the reported user, or the owner of the reported item
  target_user_id CHAR(36)    NOT NULL,
  reason         VARCHAR(20) NOT NULL,
  description    TEXT        NOT NULL,
  status         VARCHAR(12) NOT NULL DEFAULT 'OPEN',
  admin_note     TEXT        NULL,
  resolved_by    CHAR(36)    NULL,
  resolved_at    DATETIME(6) NULL,
  created_at     DATETIME(6) NOT NULL,
  updated_at     DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY reports_status_created_at_idx (status, created_at),
  KEY reports_target_type_target_id_idx (target_type, target_id),
  KEY reports_target_user_id_status_idx (target_user_id, status),
  CONSTRAINT reports_target_type_check CHECK (target_type IN ('ITEM', 'USER')),
  CONSTRAINT reports_reason_check CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'FRAUD', 'DAMAGED_ITEM', 'NOT_RETURNED', 'HARASSMENT', 'OTHER')),
  CONSTRAINT reports_status_check CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED')),
  CONSTRAINT reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES users (id),
  CONSTRAINT reports_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
  id         CHAR(36)    NOT NULL,
  actor_id   CHAR(36)    NULL,
  action     VARCHAR(60) NOT NULL,
  entity     VARCHAR(40) NOT NULL,
  entity_id  VARCHAR(64) NULL,
  metadata   JSON        NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY audit_logs_entity_entity_id_idx (entity, entity_id),
  KEY audit_logs_created_at_idx (created_at),
  CONSTRAINT audit_logs_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settings (
  setting_key VARCHAR(60) NOT NULL,
  value       JSON        NOT NULL,
  updated_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE announcements (
  id              CHAR(36)     NOT NULL,
  author_id       CHAR(36)     NOT NULL,
  title           VARCHAR(150) NOT NULL,
  body            TEXT         NOT NULL,
  link            TEXT         NULL,
  audience        VARCHAR(20)  NOT NULL,
  send_email      TINYINT(1)   NOT NULL DEFAULT 0,
  recipient_count INT          NOT NULL DEFAULT 0,
  created_at      DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT announcements_author_id_fkey FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One row per idempotent scheduled-job run, e.g. ('due-reminders', '2026-10-11').
CREATE TABLE job_runs (
  job        VARCHAR(60) NOT NULL,
  run_key    VARCHAR(60) NOT NULL,
  result     JSON        NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (job, run_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
