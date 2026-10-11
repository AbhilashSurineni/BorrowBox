package com.borrowbox.items;

import static com.borrowbox.common.errors.BusinessException.conflict;
import static com.borrowbox.common.errors.BusinessException.notFound;

import com.borrowbox.common.audit.AuditService;
import com.borrowbox.common.errors.ErrorCode;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
  private final CategoryRepository categories;
  private final ItemRepository items;
  private final AuditService audit;

  public CategoryService(CategoryRepository categories, ItemRepository items, AuditService audit) {
    this.categories = categories;
    this.items = items;
    this.audit = audit;
  }

  public record PublicCategory(String id, String name, String icon, long itemCount) {}

  public record CategoryView(String id, String name, String icon, boolean isActive, Instant createdAt, Instant updatedAt) {}

  public record AdminCategory(String id, String name, String icon, boolean isActive, Instant createdAt, Instant updatedAt, long itemCount) {}

  @Transactional(readOnly = true)
  public List<PublicCategory> listActive() {
    Map<String, Long> counts = counts(items.countVisibleByCategory());
    return categories.findByIsActiveTrueOrderByNameAsc().stream()
        .map(c -> new PublicCategory(c.id, c.name, c.icon, counts.getOrDefault(c.id, 0L)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AdminCategory> listAll() {
    Map<String, Long> counts = counts(items.countAllByCategory());
    return categories.findAllByOrderByNameAsc().stream()
        .map(c -> new AdminCategory(c.id, c.name, c.icon, c.isActive, c.createdAt, c.updatedAt, counts.getOrDefault(c.id, 0L)))
        .toList();
  }

  @Transactional
  public CategoryView create(String adminId, String name, String icon, Boolean isActive) {
    if (!categories.findByNameIgnoreCaseExcept(name, null).isEmpty()) {
      throw conflict(ErrorCode.CATEGORY_EXISTS, "A category with this name already exists");
    }
    Category c = new Category();
    c.name = name;
    c.icon = icon;
    c.isActive = isActive == null || isActive;
    categories.saveAndFlush(c);
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("name", name);
    if (icon != null) meta.put("icon", icon);
    if (isActive != null) meta.put("isActive", isActive);
    audit.log(adminId, "CATEGORY_CREATED", "Category", c.id, meta);
    return view(c);
  }

  @Transactional
  public CategoryView update(String adminId, String id, String name, String icon, Boolean isActive) {
    Category c = categories.findById(id).orElseThrow(() -> notFound("Category"));
    if (name != null && !categories.findByNameIgnoreCaseExcept(name, id).isEmpty()) {
      throw conflict(ErrorCode.CATEGORY_EXISTS, "A category with this name already exists");
    }
    CategoryView before = view(c);
    Map<String, Object> changes = new LinkedHashMap<>();
    if (name != null) {
      c.name = name;
      changes.put("name", name);
    }
    if (icon != null) {
      c.icon = icon;
      changes.put("icon", icon);
    }
    if (isActive != null) {
      c.isActive = isActive;
      changes.put("isActive", isActive);
    }
    categories.saveAndFlush(c);
    audit.log(adminId, "CATEGORY_UPDATED", "Category", id, Map.of("before", before, "changes", changes));
    return view(c);
  }

  @Transactional
  public void delete(String adminId, String id) {
    Category c = categories.findById(id).orElseThrow(() -> notFound("Category"));
    if (items.countByCategoryId(id) > 0) {
      throw conflict(ErrorCode.CATEGORY_IN_USE, "Category has items; deactivate it instead (isActive: false)");
    }
    categories.delete(c);
    audit.log(adminId, "CATEGORY_DELETED", "Category", id, Map.of("name", c.name));
  }

  private static CategoryView view(Category c) {
    return new CategoryView(c.id, c.name, c.icon, c.isActive, c.createdAt, c.updatedAt);
  }

  private static Map<String, Long> counts(List<Object[]> rows) {
    Map<String, Long> out = new HashMap<>();
    for (Object[] r : rows) out.put((String) r[0], ((Number) r[1]).longValue());
    return out;
  }
}
