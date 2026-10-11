package com.borrowbox.common.config;

import com.borrowbox.common.errors.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** JSON bodies are capped at 100 kB (413), like express.json({ limit: '100kb' }). */
public class JsonBodyLimitFilter extends OncePerRequestFilter {
  private static final long LIMIT = 100 * 1024;

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String type = req.getContentType();
    if (type != null && type.startsWith("application/json") && req.getContentLengthLong() > LIMIT) {
      res.setStatus(413);
      res.setContentType("application/json");
      res.getWriter().write("{\"error\":{\"code\":\"" + ErrorCode.VALIDATION_ERROR + "\",\"message\":\"Request body too large\"}}");
      return;
    }
    chain.doFilter(req, res);
  }
}
