package com.example.emailsystem.controller;

import com.example.emailsystem.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("form", new RegistrationForm("", "", ""));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("form") RegistrationForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.register(form.displayName(), form.email(), form.password());
            redirectAttributes.addFlashAttribute("success", "Account created. You can sign in now.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("registration.failed", ex.getMessage());
            return "auth/register";
        }
    }

    public record RegistrationForm(
            @NotBlank @Size(max = 100) String displayName,
            @Email @NotBlank @Size(max = 180) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }
}
