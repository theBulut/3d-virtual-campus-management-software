package com.campus.backend.service;

import com.campus.backend.dto.UserDto;
import com.campus.backend.dto.UserRequest;
import com.campus.backend.exception.DuplicateResourceException;
import com.campus.backend.exception.ResourceNotFoundException;
import com.campus.backend.model.User;
import com.campus.backend.repository.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    public UserService(UserRepository userRepository, ModelMapper modelMapper) {
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
    }

    public List<UserDto> findAll() {
        return userRepository.findAll(Sort.by("lastName", "firstName")).stream()
                .map(this::toDto)
                .toList();
    }

    public UserDto findById(Long id) {
        return toDto(getUserOrThrow(id));
    }

    @Transactional
    public UserDto create(UserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("A user with email " + request.getEmail() + " already exists");
        }
        User user = new User();
        apply(request, user);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto update(Long id, UserRequest request) {
        User user = getUserOrThrow(id);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(request.getEmail(), id)) {
            throw new DuplicateResourceException("A user with email " + request.getEmail() + " already exists");
        }
        apply(request, user);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        userRepository.delete(getUserOrThrow(id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    private void apply(UserRequest request, User user) {
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(request.getEmail().trim());
    }

    private UserDto toDto(User user) {
        return modelMapper.map(user, UserDto.class);
    }
}
