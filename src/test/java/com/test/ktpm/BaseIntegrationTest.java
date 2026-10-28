package com.test.ktpm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.ktpm.Security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    protected MockMvc mockMvc;

    protected ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired(required = false)
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected TestDataHelper testData;

    @BeforeEach
    public void setupBase() {
        if (springSecurityFilterChain != null) {
            this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .addFilter(springSecurityFilterChain)
                    .build();
        } else {
            this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .build();
        }
    }

    public String getBearerToken(String username, String role) {
        String token = jwtTokenProvider.generateTokenFromUsername(username, role);
        return "Bearer " + token;
    }

    public String getUserToken() {
        testData.createRegularUser();
        return getBearerToken("test_user", "ROLE_USER");
    }

    public String getAdminToken() {
        testData.createAdminUser();
        return getBearerToken("test_admin", "ROLE_ADMIN");
    }
}
