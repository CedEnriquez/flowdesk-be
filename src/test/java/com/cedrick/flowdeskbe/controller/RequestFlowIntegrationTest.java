package com.cedrick.flowdeskbe.controller;

import com.cedrick.flowdeskbe.entity.User;
import com.cedrick.flowdeskbe.enums.Role;
import com.cedrick.flowdeskbe.repository.RequestActivityRepository;
import com.cedrick.flowdeskbe.repository.ServiceRequestRepository;
import com.cedrick.flowdeskbe.repository.UserRepository;
import com.cedrick.flowdeskbe.security.AuthUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired ServiceRequestRepository requests;
    @Autowired RequestActivityRepository activities;

    RequestPostProcessor alice, bob, admin;

    @BeforeEach
    void setUp() {
        activities.deleteAll();
        requests.deleteAll();
        users.deleteAll();
        alice = login(users.save(new User("alice@test.dev", "Alice", "x", Role.REQUESTER)));
        bob = login(users.save(new User("bob@test.dev", "Bob", "x", Role.REQUESTER)));
        admin = login(users.save(new User("admin@test.dev", "Admin", "x", Role.ADMIN)));
    }

    private RequestPostProcessor login(User u) {
        AuthUserPrincipal p = new AuthUserPrincipal(u);
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private long createRequestAs(RequestPostProcessor user) throws Exception {
        MvcResult res = mvc.perform(post("/api/requests").with(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New laptop","description":"Need one","type":"SERVICE","priority":"HIGH"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();
        JsonNode node = json.readTree(res.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }

    private void transition(RequestPostProcessor user, long id, String body, int expectedStatus) throws Exception {
        mvc.perform(post("/api/requests/" + id + "/transitions").with(user).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void unauthenticatedGets401InUniformShape() throws Exception {
        mvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void blankTitleIsRejectedWith400AndFieldErrors() throws Exception {
        mvc.perform(post("/api/requests").with(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ","description":"d","type":"GENERAL","priority":"LOW"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void requesterCannotApprove_403() throws Exception {
        long id = createRequestAs(alice);
        transition(admin, id, "{\"toStatus\":\"IN_REVIEW\"}", 200);
        transition(alice, id, "{\"toStatus\":\"APPROVED\"}", 403);
    }

    @Test
    void rejectWithoutComment_400() throws Exception {
        long id = createRequestAs(alice);
        transition(admin, id, "{\"toStatus\":\"IN_REVIEW\"}", 200);
        transition(admin, id, "{\"toStatus\":\"REJECTED\"}", 400);
        transition(admin, id, "{\"toStatus\":\"REJECTED\",\"comment\":\"   \"}", 400);
    }

    @Test
    void invalidTransition_409() throws Exception {
        long id = createRequestAs(alice);
        transition(admin, id, "{\"toStatus\":\"APPROVED\"}", 409); // must be reviewed first
    }

    @Test
    void requesterCannotSeeAnothersRequest_404() throws Exception {
        long id = createRequestAs(alice);
        mvc.perform(get("/api/requests/" + id).with(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/" + id + "/activity").with(bob)).andExpect(status().isNotFound());
        transition(bob, id, "{\"toStatus\":\"CANCELLED\"}", 404);
        mvc.perform(get("/api/requests").with(bob))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/requests").with(admin))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void happyPathWritesCompleteAuditTrail() throws Exception {
        long id = createRequestAs(alice);
        transition(admin, id, "{\"toStatus\":\"IN_REVIEW\"}", 200);
        transition(admin, id, "{\"toStatus\":\"APPROVED\",\"comment\":\"ok\"}", 200);
        transition(admin, id, "{\"toStatus\":\"FULFILLED\"}", 200);

        MvcResult res = mvc.perform(get("/api/requests/" + id + "/activity").with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].previousStatus").doesNotExist())
                .andExpect(jsonPath("$.data[0].newStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data[3].newStatus").value("FULFILLED"))
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).doesNotContain("passwordHash");

        mvc.perform(get("/api/requests/" + id).with(alice))
                .andExpect(jsonPath("$.data.status").value("FULFILLED"))
                .andExpect(jsonPath("$.data.availableActions.length()").value(0));
    }

    @Test
    void ownerCanCancelButOtherAdminActionsStillBlocked() throws Exception {
        long id = createRequestAs(alice);
        transition(admin, id, "{\"toStatus\":\"CANCELLED\"}", 403); // admin is not the owner
        transition(alice, id, "{\"toStatus\":\"CANCELLED\"}", 200);
        transition(alice, id, "{\"toStatus\":\"IN_REVIEW\"}", 409);  // terminal
    }

    @Test
    void missingCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/requests").with(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/auth/me").with(alice))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-site"))
                .andExpect(header().string("X-XSS-Protection", "0"));
    }
}
