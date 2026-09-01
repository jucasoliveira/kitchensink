package com.jucasoliveira.kitchensink;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final MemberRepository members;

    HomeController(MemberRepository members) {
        this.members = members;
    }

    @GetMapping("/")
    String index(Model model) {
        model.addAttribute("members", members.findAll());
        return "index";
    }

}
