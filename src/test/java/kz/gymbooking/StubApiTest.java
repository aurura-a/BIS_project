package kz.gymbooking;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import kz.gymbooking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class StubApiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository users;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void classesAreListed() throws Exception {
        mvc.perform(get("/classes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("total: 4")))
                .andExpect(content().string(containsString("Morning Yoga")));
    }

    @Test
    void bookingFreeClass() throws Exception {
        book("{\"classId\":43}").andExpect(status().isCreated());
    }

    @Test
    void bookingErrors() throws Exception {
        book("{\"classId\":42}").andExpect(status().isConflict())
                .andExpect(content().string(containsString("code: ALREADY_BOOKED")));
        book("{\"classId\":44}").andExpect(status().isConflict())
                .andExpect(content().string(containsString("code: CLASS_FULL")));
        book("{}").andExpect(status().isUnprocessableContent());
    }

    @Test
    void checkInDenied() throws Exception {
        mvc.perform(post("/access/check-in").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"expired\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("reason: TOKEN_EXPIRED")));
    }

    @Test
    @Transactional
    void classAssignmentRequiresTrainerRole() throws Exception {
        var client = users.create("Test Client", "client-" + UUID.randomUUID() + "@example.test",
                null, "test-hash", "CLIENT");
        String startTime = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).toString();
        String classRequest = "{\"name\":\"Test yoga\",\"type\":\"yoga\",\"trainerId\":" + client.id()
                + ",\"startTime\":\"" + startTime + "\",\"durationMin\":45,\"room\":\"Test room\","
                + "\"capacity\":8}";

        mvc.perform(post("/classes").contentType(MediaType.APPLICATION_JSON).content(classRequest))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("INVALID_STATE")));

        users.updateRole(client.id(), "TRAINER");

        mvc.perform(post("/classes").contentType(MediaType.APPLICATION_JSON).content(classRequest))
                .andExpect(status().isCreated());
    }

    private ResultActions book(String json) throws Exception {
        return mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
