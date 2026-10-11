package com.borrowbox.common.storage;

/** Stores processed images and returns their public URL. */
public interface StorageService {
  String save(byte[] data, String ext);

  /** Deletes a file previously returned by save(); ignores unknown URLs. */
  void remove(String url);
}
