package com.borrowbox.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Placeholder for a Cloudinary driver (CLOUDINARY_* settings). Not implemented in the MVP. */
@Service
@ConditionalOnProperty(name = "app.storage.driver", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {
  @Override
  public String save(byte[] data, String ext) {
    throw new UnsupportedOperationException("Cloudinary storage is not implemented yet; use STORAGE_DRIVER=local");
  }

  @Override
  public void remove(String url) {
    throw new UnsupportedOperationException("Cloudinary storage is not implemented yet; use STORAGE_DRIVER=local");
  }
}
