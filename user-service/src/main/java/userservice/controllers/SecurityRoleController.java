package userservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import userservice.dto.SecurityRoleDto;
import userservice.entities.SecurityRole;
import userservice.managers.SecurityRoleManager;
import userservice.mappers.SecurityRoleMapper;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping(value = "/v1/users/role")
@RestController
public class SecurityRoleController {

    private final SecurityRoleManager securityRoleManager;
    private final SecurityRoleMapper securityRoleMapper;

    @GetMapping()
    public ResponseEntity<List<SecurityRoleDto>> getAll() {
        List<SecurityRole> roles = securityRoleManager.getRoles();
        return ResponseEntity.ok(securityRoleMapper.toDto(roles));
    }

    @PutMapping("/{userID}")
    public ResponseEntity<Void> assignRole(@PathVariable Long userID, @RequestBody @Valid SecurityRoleDto securityRoleDto) {
        securityRoleManager.assignRole(userID, securityRoleDto);
        return ResponseEntity.noContent().build();
    }
}
