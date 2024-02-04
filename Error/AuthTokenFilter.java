package me.yukitale.cryptoexchange.exchange.security.jwt;

import me.yukitale.cryptoexchange.exchange.repository.ban.EmailBanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import me.yukitale.cryptoexchange.exchange.security.service.UserDetailsServiceImpl;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public class AuthTokenFilter extends OncePerRequestFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthTokenFilter.class);

  private static final String[] PROTECTED_PATHS = {
          //
          "/", "/signin", "/signup", "/signin-2fa", "/forgot-password", "/email",
          //
          "/api/worker-panel/**", "/api/admin-panel/**", "/api/supporter-panel/**",
          "/api/user/**", "/trading", "/profile/**", "/worker-panel/**",
          "/admin-panel/**", "/supporter-panel/**", "/aml-kyc-policy", "/bugbounty",
          "/cookies-policy", "/cross-rates", "/fees", "/heat-map",
          "/indices", "/law", "/market-crypto", "/market-screener",
          "/privacy-notice", "/regulatory", "/risk", "/technical-analysis",
          "/terms", "/treatment"
  };

  @Autowired
  private EmailBanRepository emailBanRepository;

  @Autowired
  private JwtUtils jwtUtils;

  @Autowired
  private UserDetailsServiceImpl userDetailsService;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    try {
      String requestURI = request.getRequestURI().toLowerCase();

      if (requiresAuthentication(requestURI)) {
        String jwt = parseJwt(request);
        if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
          String email = jwtUtils.getEmailFromJwtToken(jwt);
          if (emailBanRepository.existsByEmail(email)) {
            response.sendRedirect("/banned");
            return;
          }

          UserDetails userDetails = userDetailsService.loadUserByUsername(email);

          if (userDetails != null) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Cannot set user authentication: {}", e);
    }

    filterChain.doFilter(request, response);
  }

  private boolean requiresAuthentication(String requestURI) {
    for (String path : PROTECTED_PATHS) {
      if (path.endsWith("/**")) {
        String basePath = path.substring(0, path.length() - 3);
        if (requestURI.startsWith(basePath)) {
          return true;
        }
      } else {
        if (requestURI.equals(path)) {
          return true;
        }
      }
    }

    return false;
  }

  private String parseJwt(HttpServletRequest request) {
    return jwtUtils.getJwtFromCookies(request);
  }
}
