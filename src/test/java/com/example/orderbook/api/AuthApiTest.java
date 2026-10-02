package com.example.orderbook.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    MockMvc mvc;

    private String creds(String user, String pass) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, pass);
    }

    @Test
    void ordersWithoutTokenReturn401() throws Exception {
        mvc.perform(get("/book")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenLoginGivesTokenThatWorks() throws Exception {
        String user = "u" + UUID.randomUUID().toString().substring(0, 8);   // har run me naya user

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(creds(user, "secret123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())));

        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(creds(user, "secret123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(response, "$.token");

        mvc.perform(get("/book").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String user = "u" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(creds(user, "secret123"))).andExpect(status().isCreated());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(creds(user, "wrongpass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        String user = "u" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(creds(user, "secret123"))).andExpect(status().isCreated());

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(creds(user, "secret123")))
                .andExpect(status().isConflict());
    }
}