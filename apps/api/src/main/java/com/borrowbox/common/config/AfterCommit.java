package com.borrowbox.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Side effects (emails) that must only happen once the surrounding transaction has committed,
 * so a rolled-back transaction never sends mail. Failures are logged, never propagated.
 */
public final class AfterCommit {
  private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

  private AfterCommit() {}

  public static void run(Runnable task) {
    Runnable safe = () -> {
      try {
        task.run();
      } catch (RuntimeException e) {
        log.error("[after-commit] side effect failed", e);
      }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          safe.run();
        }
      });
    } else {
      safe.run();
    }
  }
}
