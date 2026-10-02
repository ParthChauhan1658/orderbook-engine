package com.example.orderbook.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    MockMvc mvc;

    private long postOrder(String side, long price, long qty) throws Exception {
        String body = """
                {"side":"%s","type":"LIMIT","price":%d,"quantity":%d}
                """.formatted(side, price, qty);
        String response = mvc.perform(post("/orders").with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.orderId");
        return id.longValue();
    }

    @Test
    void matchingOrdersProduceTrade() throws Exception {
        long sellId = postOrder("SELL", 100, 5);
        long buyId = postOrder("BUY", 100, 3);

        String filter = "$[?(@.makerOrderId == %d && @.takerOrderId == %d)]"
                .formatted(sellId, buyId);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/trades?limit=500").with(user("tester")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath(filter + ".price", hasItem(100)))
                        .andExpect(jsonPath(filter + ".quantity", hasItem(3))));
    }

    @Test
    void cancelRemovesRestingOrderFromBook() throws Exception {
        long id = postOrder("SELL", 200, 5);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/book").with(user("tester")))
                        .andExpect(jsonPath("$.asks[?(@.price == 200)].quantity", hasItem(5))));

        mvc.perform(delete("/orders/" + id).with(user("tester"))).andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/book").with(user("tester")))
                        .andExpect(jsonPath("$.asks[?(@.price == 200)]", empty())));
    }

    @Test
    void invalidLimitPriceReturns400() throws Exception {
        mvc.perform(post("/orders").with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"side":"BUY","type":"LIMIT","price":0,"quantity":3}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        mvc.perform(post("/orders").with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}