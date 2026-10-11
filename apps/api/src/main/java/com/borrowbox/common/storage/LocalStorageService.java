package com.borrowbox.common.storage;

import com.borrowbox.common.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Local-disk driver; files are served by the /uploads/** resource handler. */
@Service
@ConditionalOnProperty(name = "app.storage.driver", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
  private static final String PUBLIC_PREFIX = "/uploads/";
  private final Path root;
  private final String publicUrl;

  public LocalStorageService(AppProperties props) {
    this.root = Path.of(props.storage().uploadDir()).toAbsolutePath().normalize();
    this.publicUrl = props.apiPublicUrl();
  }

  @Override
  public String save(byte[] data, String ext) {
    try {
      Files.createDirectories(root);
      String name = UUID.randomUUID() + "." + ext;
      Files.write(root.resolve(name), data);
      return publicUrl + PUBLIC_PREFIX + name;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void remove(String url) {
    if (url == null) return;
    int idx = url.indexOf(PUBLIC_PREFIX);
    if (idx == -1) return;
    String name = Path.of(url.substring(idx + PUBLIC_PREFIX.length())).getFileName().toString();
    try {
      Files.deleteIfExists(root.resolve(name));
    } catch (IOException ignored) {
      // best effort
    }
  }
}
