package com.borrowbox.common.storage;

import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates and re-encodes uploads: the real format must be JPEG, PNG or WebP (not just the
 * declared MIME type); EXIF orientation is applied; the image is re-encoded with ImageIO, which
 * drops all metadata (EXIF/GPS); size is capped at 1600px. ImageIO cannot write WebP, so WebP
 * uploads are stored as PNG (with transparency) or JPEG.
 */
@Service
public class ImageService {
  public static final List<String> ALLOWED_MIME = List.of("image/jpeg", "image/png", "image/webp");
  public static final long MAX_BYTES = 5L * 1024 * 1024;
  public static final int MAX_ITEM_IMAGES = 5;
  private static final int MAX_DIMENSION = 1600;

  private final StorageService storage;

  public ImageService(StorageService storage) {
    this.storage = storage;
  }

  /** Checks the declared type and size of an upload (multer fileFilter/limits). */
  public static void checkUpload(MultipartFile file) {
    if (file.getSize() > MAX_BYTES) {
      throw new BusinessException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.INVALID_IMAGE, "Each image must be 5 MB or smaller");
    }
    if (file.getContentType() == null || !ALLOWED_MIME.contains(file.getContentType())) {
      throw BusinessException.unprocessable(ErrorCode.INVALID_IMAGE, "Only JPEG, PNG and WebP images are allowed");
    }
  }

  public String store(MultipartFile file) {
    try {
      Processed p = process(file.getBytes());
      return storage.save(p.data(), p.ext());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void remove(String url) {
    storage.remove(url);
  }

  public record Processed(byte[] data, String ext) {}

  public Processed process(byte[] input) {
    String format;
    BufferedImage image;
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
      if (readers == null || !readers.hasNext()) throw invalid("File is not a valid image");
      ImageReader reader = readers.next();
      format = normalize(reader.getFormatName());
      if (format == null) {
        throw BusinessException.unprocessable(ErrorCode.INVALID_IMAGE, "Only JPEG, PNG and WebP images are allowed");
      }
      reader.setInput(in);
      image = reader.read(0);
      reader.dispose();
    } catch (IOException | RuntimeException e) {
      if (e instanceof BusinessException be) throw be;
      throw invalid("File is not a valid image");
    }
    image = resize(applyOrientation(image, orientation(input)));
    boolean alpha = image.getColorModel().hasAlpha();
    String out = switch (format) {
      case "jpeg" -> "jpg";
      case "png" -> "png";
      default -> alpha ? "png" : "jpg";
    };
    return new Processed(out.equals("jpg") ? writeJpeg(image) : writePng(image), out);
  }

  private static BusinessException invalid(String message) {
    return BusinessException.unprocessable(ErrorCode.INVALID_IMAGE, message);
  }

  private static String normalize(String formatName) {
    String f = formatName.toLowerCase(Locale.ROOT);
    if (f.equals("jpeg") || f.equals("jpg")) return "jpeg";
    if (f.equals("png")) return "png";
    if (f.equals("webp")) return "webp";
    return null;
  }

  private static int orientation(byte[] input) {
    try {
      Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(input));
      ExifIFD0Directory dir = md.getFirstDirectoryOfType(ExifIFD0Directory.class);
      if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
    } catch (Exception ignored) {
      // no EXIF
    }
    return 1;
  }

  private static BufferedImage applyOrientation(BufferedImage img, int orientation) {
    if (orientation <= 1 || orientation > 8) return img;
    int w = img.getWidth();
    int h = img.getHeight();
    AffineTransform t = new AffineTransform();
    switch (orientation) {
      case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
      case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
      case 4 -> { t.scale(1, -1); t.translate(0, -h); }
      case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
      case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
      case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
      case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
      default -> { return img; }
    }
    return new AffineTransformOp(t, AffineTransformOp.TYPE_BICUBIC).filter(img, null);
  }

  private static BufferedImage resize(BufferedImage img) {
    int w = img.getWidth();
    int h = img.getHeight();
    if (w <= MAX_DIMENSION && h <= MAX_DIMENSION) return img;
    double scale = Math.min((double) MAX_DIMENSION / w, (double) MAX_DIMENSION / h);
    int nw = Math.max(1, (int) Math.round(w * scale));
    int nh = Math.max(1, (int) Math.round(h * scale));
    int type = img.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    BufferedImage out = new BufferedImage(nw, nh, type);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.drawImage(img, 0, 0, nw, nh, null);
    g.dispose();
    return out;
  }

  private static byte[] writePng(BufferedImage img) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(img, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] writeJpeg(BufferedImage img) {
    BufferedImage rgb = img;
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = rgb.createGraphics();
      g.setColor(java.awt.Color.WHITE);
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
      g.drawImage(img, 0, 0, null);
      g.dispose();
    }
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.9f);
      writer.write(null, new IIOImage(rgb, null, null), param);
      ios.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      writer.dispose();
    }
  }
}
