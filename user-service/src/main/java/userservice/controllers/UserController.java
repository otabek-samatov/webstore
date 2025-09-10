package userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import userservice.dto.UserDto;
import userservice.entities.User;
import userservice.managers.UserManager;
import userservice.mappers.UserMapper;

@RequiredArgsConstructor
@RequestMapping(value = "v1/users/user")
@RestController
public class UserController {

    private final UserManager userManager;
    private final UserMapper userMapper;

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody UserDto userDto) {
        User user = userManager.createUser(userDto);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getById(@PathVariable Long id) {
        User user = userManager.getUserByID(id);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @PostMapping
    public ResponseEntity<UserDto> update(@RequestBody UserDto userDto) {
        User user = userManager.update(userDto);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        userManager.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
