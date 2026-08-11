package authservice.security;

import authservice.entities.AppUser;
import authservice.repositories.AppUserRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;

public class AuthUserDetailsManager implements UserDetailsManager {

    private final AppUserRepository appUserRepository;

    public AuthUserDetailsManager(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public void createUser(UserDetails user) {
        if (appUserRepository.existsAppUserByUserName(user.getUsername())) {
            throw new UsernameNotFoundException("Username already exists");
        }

        AppUser appUser = new AppUser();
        appUser.setAuthorities(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        appUser.setUserName(user.getUsername());
        appUser.setPassword(user.getPassword());
        appUserRepository.save(appUser);
    }

    @Override
    public void updateUser(UserDetails user) {

        AppUser appUser = appUserRepository.findByUsername(user.getUsername()).orElse(null);
        if (appUser == null) {
            throw new UsernameNotFoundException("Username not found");
        }

        appUser.setAuthorities(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        appUserRepository.save(appUser);
    }

    @Override
    public void deleteUser(String username) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user != null) {
            appUserRepository.delete(user);
        }

    }

    @Override
    public void changePassword(@Nullable String oldPassword, @Nullable String newPassword) {

    }

    @Override
    public boolean userExists(String username) {
        return appUserRepository.existsAppUserByUserName(username);
    }

    @Override
    public @Nullable UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return appUserRepository.findByUsername(username).map(SecurityUser::new).orElse(null);
    }
}
