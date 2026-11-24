package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HardeningFiltersTest {

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void addsSecurityHeadersAndDisablesCachingForTheApi() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(request("/api/rules"), response, new MockFilterChain());

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void leavesCachingAloneOutsideTheApi() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(request("/swagger-ui.html"), response, new MockFilterChain());

        assertThat(response.getHeader("Cache-Control")).isNull();
    }

    @Test
    void rejectsBodiesAboveTheLimit() throws Exception {
        MockHttpServletRequest request = request("/api/rules");
        request.setContent(new byte[11]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new BodySizeLimitFilter(10).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void acceptsBodiesWithinTheLimit() throws Exception {
        MockHttpServletRequest request = request("/api/rules");
        request.setContent(new byte[10]);
        MockFilterChain chain = new MockFilterChain();

        new BodySizeLimitFilter(10).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
