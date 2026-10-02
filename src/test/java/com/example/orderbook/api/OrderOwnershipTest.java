package com.example.orderbook.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderOwnershipTest {

    @Autowired
    MockMvc mvc;

    private long postAs(String username, String side, long price, long qty) throws Exception {
        String body = """
                {"side":"%s","type":"LIMIT","price":%d,"quantity":%d}
                """.formatted(side, price, qty);
        String response = mvc.perform(post("/orders").with(user(username))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.orderId")).longValue();
    }

    @Test
    void userCannotCancelAnotherUsersOrder() throws Exception {
        long id = postAs("alice", "SELL", 300, 5);

        mvc.perform(delete("/orders/" + id).with(user("bob")))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/orders/" + id).with(user("alice")))
                .andExpect(status().isAccepted());
    }

    @Test
    void mineShowsOnlyOwnOrders() throws Exception {
        long aliceOrder = postAs("alice2", "SELL", 400, 5);
        long bobOrder = postAs("bob2", "SELL", 410, 5);

        mvc.perform(get("/orders/mine?size=100").with(user("alice2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) aliceOrder)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) bobOrder))));
    }

    @Test
    void cancelUnknownOrderReturns404() throws Exception {
        mvc.perform(delete("/orders/999999").with(user("alice")))
                .andExpect(status().isNotFound());
    }
}