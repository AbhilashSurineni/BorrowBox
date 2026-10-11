package com.borrowbox.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Placeholder for an AWS S3 driver. Not implemented in the MVP. */
@Service
@ConditionalOnProperty(name = "app.storage.driver", havingValue = "s3")
public class S3StorageService implements StorageService {
  @Override
  public String save(byte[] data, String ext) {
    throw new UnsupportedOperationException("S3 storage is not implemented yet; use STORAGE_DRIVER=local");
  }

  @Override
  public void remove(String url) {
    throw new UnsupportedOperationException("S3 storage is not implemented yet; use STORAGE_DRIVER=local");
  }
}
