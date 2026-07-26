package com.campus.backend.service;

import com.campus.backend.dto.AdminDto;
import com.campus.backend.exception.ResourceNotFoundException;
import com.campus.backend.model.Admin;
import com.campus.backend.repository.AdminRepository;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;
    private final ModelMapper modelMapper;

    public AdminService(AdminRepository adminRepository, ModelMapper modelMapper) {
        this.adminRepository = adminRepository;
        this.modelMapper = modelMapper;
    }

    public List<AdminDto> findAll() {
        return adminRepository.findAll(Sort.by("username")).stream()
                .map(admin -> modelMapper.map(admin, AdminDto.class))
                .toList();
    }

    public AdminDto findByUsername(String username) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin " + username + " not found"));
        return modelMapper.map(admin, AdminDto.class);
    }
}
