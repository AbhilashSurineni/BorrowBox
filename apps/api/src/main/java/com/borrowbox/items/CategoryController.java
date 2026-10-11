package com.borrowbox.items;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController {
  private final CategoryService categories;

  public CategoryController(CategoryService categories) {
    this.categories = categories;
  }

  /** Public list of active categories with their visible-item counts. */
  @GetMapping("/api/v1/categories")
  public List<CategoryService.PublicCategory> list() {
    return categories.listActive();
  }
}
