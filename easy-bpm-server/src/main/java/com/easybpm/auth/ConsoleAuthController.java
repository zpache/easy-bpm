package com.easybpm.auth;

import com.easybpm.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class ConsoleAuthController {

    private final ConsoleAuthService authService;

    public ConsoleAuthController(ConsoleAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<ConsoleAuthService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.tenantCode(), request.username(), request.password()));
    }

    @GetMapping("/me")
    public ApiResponse<ConsoleAuthService.LoginResult> me() {
        return ApiResponse.ok(authService.me(CurrentUser.require()));
    }

    public record LoginRequest(@NotBlank String tenantCode, @NotBlank String username, @NotBlank String password) {
    }
}
