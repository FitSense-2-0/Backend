package main.web.services.fitsense.iam.infrastructure.authorization.sfs.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import main.web.services.fitsense.iam.domain.model.aggregates.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@EqualsAndHashCode
public class UserDetailsImpl implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    private UserDetailsImpl(Long userId, String username, String password, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.accountNonLocked = enabled;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public static UserDetailsImpl build(User user) {
        return new UserDetailsImpl(user.getId(), user.emailAddress(),
                user.getPasswordHash(), user.canSignIn());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return accountNonLocked;
    }
}
