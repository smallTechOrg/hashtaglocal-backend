package org.smalltech.hashtaglocal_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.config.SecurityConfig;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.model.response.AiProviderConfigData;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.security.AccessTokenAuthFilter;
import org.smalltech.hashtaglocal_backend.service.AiProviderConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The AI connection routes are admin-only, enforced on the backend by the real security chain and
 * auth filter (not just by hiding the page).
 */
@WebMvcTest(AiProviderAdminController.class)
@Import({SecurityConfig.class, AccessTokenAuthFilter.class})
class AiProviderAdminControllerSecurityTest {

  private static final String SECRET = "sk-ant-secret-value-1234";
  private static final String BODY =
      "{\"name\":\"Claude\",\"endpoint\":\"https://api.anthropic.com/v1\",\"model\":\"claude-x\",\"api_key\":\""
          + SECRET
          + "\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private AiProviderConfigService service;
  @MockitoBean private UserAuthSessionRepository sessions;

  private final AiProviderConfigData config =
      AiProviderConfigData.builder()
          .source(AiProviderConfigData.Source.DATABASE)
          .name("Claude")
          .model("claude-x")
          .hasKey(true)
          .keyLast4("1234")
          .envChain(List.of())
          .build();

  @BeforeEach
  void setUp() {
    session("admin-token", 1L, UserRole.ADMIN);
    session("user-token", 2L, UserRole.USER);
    when(service.get()).thenReturn(config);
    when(service.save(any(), eq(1L))).thenReturn(config);
  }

  @Test
  void anonymousCallersAreRejected() throws Exception {
    mvc.perform(get("/admin/ai-provider")).andExpect(status().isUnauthorized());
    mvc.perform(json(put("/admin/ai-provider"))).andExpect(status().isUnauthorized());
    mvc.perform(json(post("/admin/ai-provider/test"))).andExpect(status().isUnauthorized());
    mvc.perform(delete("/admin/ai-provider")).andExpect(status().isUnauthorized());
    verify(service, never()).get();
    verify(service, never()).save(any(), any());
  }

  @Test
  void nonAdminUsersAreForbidden() throws Exception {
    mvc.perform(get("/admin/ai-provider").header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
    mvc.perform(json(put("/admin/ai-provider")).header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
    mvc.perform(json(post("/admin/ai-provider/test")).header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/admin/ai-provider").header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
    verify(service, never()).get();
    verify(service, never()).save(any(), any());
    verify(service, never()).test(any());
    verify(service, never()).reset(any());
  }

  @Test
  void adminsCanReadAndTheKeyIsNeverInTheResponse() throws Exception {
    mvc.perform(get("/admin/ai-provider").header("Authorization", "Bearer admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.has_key").value(true))
        .andExpect(jsonPath("$.data.key_last4").value("1234"))
        .andExpect(jsonPath("$.data.api_key").doesNotExist())
        .andExpect(content().string(Matchers.not(Matchers.containsString("ciphertext"))));
  }

  @Test
  void adminSaveIsAttributedToTheAdminAndDoesNotEchoTheKey() throws Exception {
    mvc.perform(json(put("/admin/ai-provider")).header("Authorization", "Bearer admin-token"))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.not(Matchers.containsString(SECRET))));
    verify(service).save(any(), eq(1L));
  }

  private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
    return request.contentType(MediaType.APPLICATION_JSON).content(BODY);
  }

  private void session(String token, Long userId, UserRole role) {
    UserEntity user = UserEntity.builder().id(userId).username("u" + userId).role(role).build();
    UserAuthSessionEntity session =
        UserAuthSessionEntity.builder()
            .user(user)
            .accessToken(token)
            .accessTokenExpiryTs(Instant.now().getEpochSecond() + 3600)
            .build();
    when(sessions.findByAccessToken(token)).thenReturn(Optional.of(session));
  }
}
