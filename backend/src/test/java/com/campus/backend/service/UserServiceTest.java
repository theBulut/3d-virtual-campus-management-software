package com.campus.backend.service;

import com.campus.backend.dto.UserDto;
import com.campus.backend.dto.UserRequest;
import com.campus.backend.exception.DuplicateResourceException;
import com.campus.backend.exception.ResourceNotFoundException;
import com.campus.backend.model.User;
import com.campus.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, new ModelMapper());
    }

    @Test
    void createsUserAndTrimsInput() {
        when(userRepository.existsByEmailIgnoreCase("ada@campus.example")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        UserDto created = userService.create(
                new UserRequest("  Ada  ", "  Lovelace  ", "ada@campus.example"));

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Ada");
        assertThat(userCaptor.getValue().getLastName()).isEqualTo("Lovelace");
        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getEmail()).isEqualTo("ada@campus.example");
    }

    @Test
    void rejectsDuplicateEmailOnCreate() {
        when(userRepository.existsByEmailIgnoreCase("ada@campus.example")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new UserRequest("Ada", "Lovelace", "ada@campus.example")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updatesAllAttributes() {
        User existing = new User(1L, "Ada", "Lovelace", "ada@campus.example");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("grace@campus.example", 1L))
                .thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto updated = userService.update(1L,
                new UserRequest("Grace", "Hopper", "grace@campus.example"));

        assertThat(updated.getFirstName()).isEqualTo("Grace");
        assertThat(updated.getLastName()).isEqualTo("Hopper");
        assertThat(updated.getEmail()).isEqualTo("grace@campus.example");
    }

    @Test
    void rejectsEmailAlreadyUsedByAnotherUserOnUpdate() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(new User(1L, "Ada", "Lovelace", "ada@campus.example")));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("grace@campus.example", 1L))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L,
                new UserRequest("Ada", "Lovelace", "grace@campus.example")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void throwsWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deletesExistingUser() {
        User existing = new User(1L, "Ada", "Lovelace", "ada@campus.example");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        userService.delete(1L);

        verify(userRepository).delete(existing);
    }

    @Test
    void mapsAllUsersToDtos() {
        when(userRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(new User(1L, "Ada", "Lovelace", "ada@campus.example")));

        List<UserDto> users = userService.findAll();

        assertThat(users).singleElement()
                .satisfies(user -> assertThat(user.getEmail()).isEqualTo("ada@campus.example"));
    }
}
