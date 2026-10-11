package com.borrowbox.users;

import com.borrowbox.common.config.AppProperties;
import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.web.Ids;
import com.borrowbox.common.web.Paged;
import com.borrowbox.common.web.QueryParams;
import com.borrowbox.common.web.RequestBinder;
import com.borrowbox.items.ItemService;
import com.borrowbox.items.ItemViews.MyItem;
import com.borrowbox.users.UserRequests.UpdateProfileRequest;
import com.borrowbox.users.UserService.PublicProfile;
import com.borrowbox.users.UserService.ReviewView;
import com.borrowbox.users.UserService.UpdateProfile;
import com.borrowbox.users.UserViews.PrivateUser;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService users;
  private final ItemService items;
  private final RequestBinder binder;
  private final AppProperties props;

  public UserController(UserService users, ItemService items, RequestBinder binder, AppProperties props) {
    this.users = users;
    this.items = items;
    this.binder = binder;
    this.props = props;
  }

  @GetMapping("/me")
  public PrivateUser me(AuthUser user) {
    return users.getMe(user.id());
  }

  @PatchMapping(path = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public PrivateUser updateMultipart(AuthUser user, MultipartHttpServletRequest req) {
    for (String field : req.getMultiFileMap().keySet()) {
      if (!field.equals("avatar")) throw BusinessException.unprocessable(ErrorCode.TOO_MANY_IMAGES, "Up to 5 images are allowed");
    }
    List<MultipartFile> avatars = req.getFiles("avatar");
    if (avatars.size() > 1) throw BusinessException.unprocessable(ErrorCode.TOO_MANY_IMAGES, "Up to 5 images are allowed");
    MultipartFile avatar = avatars.isEmpty() || avatars.get(0).isEmpty() ? null : avatars.get(0);
    return users.updateMe(user.id(), command(RequestBinder.formFields(req.getParameterMap())), avatar);
  }

  @PatchMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
  public PrivateUser updateJson(AuthUser user, @RequestBody Map<String, Object> json) {
    return users.updateMe(user.id(), command(json), null);
  }

  @DeleteMapping("/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMe(AuthUser user, HttpServletResponse res) {
    users.deleteMe(user.id());
    res.addHeader("Set-Cookie", com.borrowbox.auth.RefreshCookie.clear(props).toString());
  }

  @GetMapping("/me/items")
  public Paged<MyItem> myItems(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    String status = q.string("status", 10);
    if (status == null) status = "all";
    if (!List.of("listed", "paused", "all").contains(status)) q.errors().field("status", "Invalid enum value '" + status + "'");
    q.done();
    return items.listMine(user.id(), status, page);
  }

  @GetMapping("/{id}")
  public PublicProfile profile(@PathVariable String id) {
    return users.getPublicProfile(Ids.require(id));
  }

  @GetMapping("/{id}/reviews")
  public Paged<ReviewView> reviews(@PathVariable String id, @RequestParam Map<String, String> params) {
    String userId = Ids.require(id);
    QueryParams q = new QueryParams(params);
    var page = q.page();
    q.done();
    return users.listReviews(userId, page);
  }

  private UpdateProfile command(Map<String, Object> raw) {
    Object phone = raw.get("phone");
    boolean clearPhone = raw.containsKey("phone") && (phone == null || "".equals(phone));
    Map<String, Object> fields = new HashMap<>();
    raw.forEach((k, v) -> {
      if (v != null && !"".equals(v)) fields.put(k, v);
    });
    UpdateProfileRequest body = binder.bind(fields, UpdateProfileRequest.class);
    return new UpdateProfile(body, clearPhone, Boolean.TRUE.equals(body.removeAvatar()));
  }
}
