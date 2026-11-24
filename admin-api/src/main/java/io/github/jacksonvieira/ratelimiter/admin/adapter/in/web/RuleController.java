package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import io.github.jacksonvieira.ratelimiter.admin.application.RuleService;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
class RuleController {

    private final RuleService service;

    @GetMapping
    List<RuleResponse> list() {
        return service.list().stream().map(RuleResponse::from).toList();
    }

    @GetMapping("/{id}")
    RuleResponse get(@PathVariable("id") long id) {
        return RuleResponse.from(service.get(id));
    }

    @PostMapping
    ResponseEntity<RuleResponse> create(@Valid @RequestBody RuleRequest request) {
        Rule created = service.create(request.toDefinition());
        return ResponseEntity.created(URI.create("/api/rules/" + created.id())).body(RuleResponse.from(created));
    }

    @PutMapping("/{id}")
    RuleResponse update(@PathVariable("id") long id, @Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(service.update(id, request.toDefinition()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable("id") long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
