package com.borrowbox.items;

import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.errors.ValidationErrors;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.security.OptionalAuth;
import com.borrowbox.common.security.Verified;
import com.borrowbox.common.storage.ImageService;
import com.borrowbox.common.web.Ids;
import com.borrowbox.common.web.Paged;
import com.borrowbox.common.web.QueryParams;
import com.borrowbox.common.web.RequestBinder;
import com.borrowbox.items.ItemRequests.CreateItemRequest;
import com.borrowbox.items.ItemRequests.UpdateItemRequest;
import com.borrowbox.items.ItemViews.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** HTTP layer for items. Create/update accept multipart/form-data (images) or JSON. */
@RestController
@RequestMapping("/api/v1/items")
public class ItemController {
  private final ItemService items;
  private final RequestBinder binder;
  private final ObjectMapper mapper;

  public ItemController(ItemService items, RequestBinder binder, ObjectMapper mapper) {
    this.items = items;
    this.binder = binder;
    this.mapper = mapper;
  }

  @GetMapping
  public Paged<ItemSummary> list(@RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    String sort = q.string("sort", 30);
    if (sort == null) sort = "newest";
    if (!List.of("newest", "rating", "soonest_available").contains(sort)) q.errors().field("sort", "Invalid enum value '" + sort + "'");
    var criteria = new ItemSearchRepository.Criteria(
        q.string("q", 100),
        q.uuid("category"),
        q.enumValue("condition", ItemCondition.class),
        q.bool("available"),
        q.date("available_from"),
        q.date("available_to"),
        q.string("location", 100),
        q.integer("max_duration", 1, 365),
        q.decimal("min_rating", 0, 5),
        sort,
        page.offset(),
        page.limit());
    q.done();
    return items.list(criteria, page);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ItemView createMultipart(@Verified AuthUser user, MultipartHttpServletRequest req) {
    List<MultipartFile> files = images(req);
    CreateItemRequest body = binder.bind(nonBlank(RequestBinder.formFields(req.getParameterMap())), CreateItemRequest.class);
    return items.create(user.id(), body, files);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ItemView createJson(@Verified AuthUser user, @RequestBody Map<String, Object> json) {
    return items.create(user.id(), binder.bind(nonBlank(json), CreateItemRequest.class), List.of());
  }

  @GetMapping("/{id}")
  public ItemDetail get(@OptionalAuth AuthUser user, @PathVariable String id) {
    return items.getById(Ids.require(id), user);
  }

  @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ItemDetail updateMultipart(@Verified AuthUser user, @PathVariable String id, MultipartHttpServletRequest req) {
    String itemId = Ids.require(id);
    List<MultipartFile> files = images(req);
    return items.update(itemId, user, updateCommand(RequestBinder.formFields(req.getParameterMap())), files);
  }

  @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ItemDetail updateJson(@Verified AuthUser user, @PathVariable String id, @RequestBody Map<String, Object> json) {
    return items.update(Ids.require(id), user, updateCommand(json), List.of());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(AuthUser user, @PathVariable String id) {
    items.remove(Ids.require(id), user);
  }

  @PostMapping("/{id}/pause")
  public ItemDetail pause(AuthUser user, @PathVariable String id) {
    return items.togglePause(Ids.require(id), user);
  }

  @GetMapping("/{id}/availability")
  public Availability availability(@PathVariable String id) {
    return items.availability(Ids.require(id));
  }

  // ---------------------------------------------------------------- request helpers

  private ItemService.UpdateItem updateCommand(Map<String, Object> raw) {
    Object deposit = raw.get("depositNote");
    boolean clearDeposit = raw.containsKey("depositNote") && (deposit == null || "".equals(deposit));
    List<String> removeIds = parseRemoveImageIds(raw.get("removeImageIds"));
    Map<String, Object> fields = nonBlank(raw);
    fields.remove("removeImageIds");
    return new ItemService.UpdateItem(binder.bind(fields, UpdateItemRequest.class), clearDeposit, removeIds);
  }

  /** removeImageIds: JSON array, JSON-array string, or comma-separated string; ≤ 5 UUIDs. */
  private List<String> parseRemoveImageIds(Object value) {
    if (value == null || "".equals(value)) return null;
    List<String> ids;
    try {
      if (value instanceof List<?> list) ids = list.stream().map(String::valueOf).toList();
      else if (value instanceof String[] arr) ids = Arrays.asList(arr);
      else {
        String s = value.toString().trim();
        ids = s.startsWith("[")
            ? mapper.readValue(s, new TypeReference<List<String>>() {})
            : Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
      }
    } catch (Exception e) {
      throw ValidationErrors.single("removeImageIds", "Expected array, received string");
    }
    if (ids.size() > 5) throw ValidationErrors.single("removeImageIds", "Array must contain at most 5 element(s)");
    for (String id : ids) if (!Ids.isUuid(id)) throw ValidationErrors.single("removeImageIds", "Invalid uuid");
    return ids;
  }

  /** Multer semantics: only the "images" field, at most 5 files, JPEG/PNG/WebP up to 5 MB each. */
  static List<MultipartFile> images(MultipartHttpServletRequest req) {
    for (String field : req.getMultiFileMap().keySet()) {
      if (!field.equals("images")) throw BusinessException.unprocessable(ErrorCode.TOO_MANY_IMAGES, "Up to 5 images are allowed");
    }
    List<MultipartFile> files = new ArrayList<>(req.getFiles("images"));
    files.removeIf(MultipartFile::isEmpty);
    if (files.size() > ImageService.MAX_ITEM_IMAGES) throw BusinessException.unprocessable(ErrorCode.TOO_MANY_IMAGES, "Up to 5 images are allowed");
    files.forEach(ImageService::checkUpload);
    return files;
  }

  /** Blank form values count as absent (TS emptyToUndefined). */
  static Map<String, Object> nonBlank(Map<String, ?> raw) {
    Map<String, Object> out = new HashMap<>();
    raw.forEach((k, v) -> {
      if (v != null && !"".equals(v)) out.put(k, v);
    });
    return out;
  }
}
