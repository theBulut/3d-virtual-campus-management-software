package com.campus.backend.controller;

import com.campus.backend.dto.AdminDto;
import com.campus.backend.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admins")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public List<AdminDto> findAll() {
        return adminService.findAll();
    }

    @GetMapping("/{username}")
    public AdminDto findByUsername(@PathVariable String username) {
        return adminService.findByUsername(username);
    }
}
