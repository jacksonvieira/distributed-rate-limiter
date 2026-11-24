package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import io.github.jacksonvieira.ratelimiter.admin.application.port.CreateRule;
import io.github.jacksonvieira.ratelimiter.admin.application.port.DeleteRule;
import io.github.jacksonvieira.ratelimiter.admin.application.port.GetRule;
import io.github.jacksonvieira.ratelimiter.admin.application.port.ListRules;
import io.github.jacksonvieira.ratelimiter.admin.application.port.UpdateRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Slf4j
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Tag(name = "Rules", description = "Manage the gateway's rate limit rules. The first matching rule wins.")
class RuleController {

    private final ListRules listRules;
    private final GetRule getRule;
    private final CreateRule createRule;
    private final UpdateRule updateRule;
    private final DeleteRule deleteRule;
    private final RuleWebMapper mapper;

    @GetMapping
    @Operation(summary = "List rules in evaluation order")
    List<EntityModel<RuleResponse>> list() {
        log.info("GET /api/rules");
        List<EntityModel<RuleResponse>> body = mapper.toResponses(listRules.execute()).stream()
                .map(this::withLinks).toList();
        log.info("GET /api/rules -> {} rules", body.size());
        log.debug("GET /api/rules -> {}", body);
        return body;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a rule", responses = {
            @ApiResponse(responseCode = "200", description = "Rule found"),
            @ApiResponse(responseCode = "404", description = "Unknown rule")})
    EntityModel<RuleResponse> get(@PathVariable("id") long id) {
        log.info("GET /api/rules/{}", id);
        EntityModel<RuleResponse> body = withLinks(mapper.toResponse(getRule.execute(id)));
        log.info("GET /api/rules/{} -> {}", id, body);
        return body;
    }

    @PostMapping
    @Operation(summary = "Create a rule", responses = {
            @ApiResponse(responseCode = "201", description = "Rule created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Duplicate rule name")})
    ResponseEntity<EntityModel<RuleResponse>> create(@Valid @RequestBody RuleRequest request) {
        log.info("POST /api/rules {}", request);
        Rule created = createRule.execute(mapper.toDefinition(request));
        EntityModel<RuleResponse> body = withLinks(mapper.toResponse(created));
        log.info("POST /api/rules -> 201 {}", body);
        return ResponseEntity.created(URI.create("/api/rules/" + created.id())).body(body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a rule, keeping its position", responses = {
            @ApiResponse(responseCode = "200", description = "Rule updated"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Unknown rule"),
            @ApiResponse(responseCode = "409", description = "Duplicate rule name")})
    EntityModel<RuleResponse> update(@PathVariable("id") long id, @Valid @RequestBody RuleRequest request) {
        log.info("PUT /api/rules/{} {}", id, request);
        EntityModel<RuleResponse> body =
                withLinks(mapper.toResponse(updateRule.execute(id, mapper.toDefinition(request))));
        log.info("PUT /api/rules/{} -> {}", id, body);
        return body;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a rule", responses = {
            @ApiResponse(responseCode = "204", description = "Rule deleted"),
            @ApiResponse(responseCode = "404", description = "Unknown rule")})
    ResponseEntity<Void> delete(@PathVariable("id") long id) {
        log.info("DELETE /api/rules/{}", id);
        deleteRule.execute(id);
        log.info("DELETE /api/rules/{} -> 204", id);
        return ResponseEntity.noContent().build();
    }

    /** Adds {@code self} and {@code rules} links so clients can navigate without building URLs. */
    private EntityModel<RuleResponse> withLinks(RuleResponse rule) {
        Link self = linkTo(methodOn(RuleController.class).get(rule.id())).withSelfRel();
        Link all = linkTo(methodOn(RuleController.class).list()).withRel("rules");
        return EntityModel.of(rule, self, all);
    }
}
