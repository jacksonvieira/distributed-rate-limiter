package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyFilterTest {

    private final ApiKeyFilter filter = new ApiKeyFilter("secret");

    private MockHttpServletResponse call(String uri, String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (key != null) {
            request.addHeader(ApiKeyFilter.HEADER, key);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        if (chain.getRequest() == null) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
        return response;
    }

    @Test
    void rejectsMissingKey() throws Exception {
        assertThat(call("/api/rules", null).getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsWrongKey() throws Exception {
        assertThat(call("/api/rules", "nope").getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsCorrectKey() throws Exception {
        assertThat(call("/api/rules", "secret").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesOtherPathsOpen() throws Exception {
        assertThat(call("/swagger-ui.html", null).getStatus()).isEqualTo(200);
        assertThat(call("/actuator/health", null).getStatus()).isEqualTo(200);
    }
}
