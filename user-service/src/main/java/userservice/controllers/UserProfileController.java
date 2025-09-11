package userservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import userservice.dto.UserProfileDto;
import userservice.entities.UserProfile;
import userservice.managers.UserProfileManager;
import userservice.mappers.UserProfileMapper;

@RequiredArgsConstructor
@RequestMapping(value = "v1/users/profile")
@RestController
public class UserProfileController {

    private final UserProfileManager userProfileManager;
    private final UserProfileMapper userProfileMapper;

    @PostMapping
    public ResponseEntity<UserProfileDto> create(@RequestBody @Valid UserProfileDto userDto) {
        UserProfile userProfile = userProfileManager.createUser(userDto);
        return ResponseEntity.ok(userProfileMapper.toDto(userProfile));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileDto> getById(@PathVariable Long id) {
        UserProfile userProfile = userProfileManager.getUserByID(id);
        return ResponseEntity.ok(userProfileMapper.toDto(userProfile));
    }

    @PutMapping
    public ResponseEntity<UserProfileDto> update(@RequestBody @Valid UserProfileDto userDto) {
        UserProfile userProfile = userProfileManager.update(userDto);
        return ResponseEntity.ok(userProfileMapper.toDto(userProfile));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        userProfileManager.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
