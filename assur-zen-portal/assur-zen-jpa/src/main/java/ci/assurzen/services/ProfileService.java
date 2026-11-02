package ci.assurzen.services;

import ci.assurzen.dto.ProfileRequest;
import ci.assurzen.dto.ProfileResponse;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.entities.VillePartenaire;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VillePartenaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository repository;
    private final CodeGeneratorService codeGeneratorService;
    private final UserRepository userRepository;
    private final VillePartenaireRepository villePartenaireRepository;

    public List<ProfileResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "creation"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ProfileResponse findById(Long id) {
        return toResponse(getProfile(id));
    }

    public ProfileResponse create(ProfileRequest request) {
        checkEmailUnique(request.email(), null);
        Profile profile = new Profile();
        apply(request, profile, true);
        return toResponse(repository.save(profile));
    }

    public ProfileResponse update(Long id, ProfileRequest request) {
        checkEmailUnique(request.email(), id);
        Profile profile = getProfile(id);
        apply(request, profile, false);
        return toResponse(repository.save(profile));
    }

    private void checkEmailUnique(String email, Long excludeId) {
        if (email == null || email.isBlank()) {
            return;
        }
        repository.findByEmailIgnoreCase(email)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Un profil existe deja pour cet email: " + email);
                });
    }

    public void delete(Long id) {
        repository.delete(getProfile(id));
    }

    private Profile getProfile(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile introuvable: " + id));
    }

    private void apply(ProfileRequest request, Profile profile, boolean create) {
        profile.setCode(resolveCode(request.code(), profile.getCode(), create));
        profile.setFirstname(request.firstname());
        profile.setLastname(request.lastname());
        profile.setPhone(request.phone());
        profile.setRole(request.role());
        profile.setActive(request.active() == null ? true : request.active());
        profile.setExpiration(request.expiration() != null && request.expiration());
        profile.setBio(request.bio());
        profile.setFacebookUrl(request.facebookUrl());
        profile.setTwitterUrl(request.twitterUrl());
        profile.setLinkedinUrl(request.linkedinUrl());
        profile.setInstagramUrl(request.instagramUrl());
        profile.setCountry(request.country());
        profile.setCity(request.city());
        profile.setState(request.state());
        profile.setPostalCode(request.postalCode());
        profile.setTaxId(request.taxId());
        profile.setEmail(request.email());
        profile.setSector(request.sector());
        profile.setAvatarUrl(request.avatarUrl());
        profile.setLangueCode(request.langueCode());
        if (request.twoFactorEnabled() != null || create) {
            profile.setTwoFactorEnabled(request.twoFactorEnabled() == null || request.twoFactorEnabled());
        }
        profile.setUser(resolveUser(request.userId()));
        profile.setVillePartenaire(resolveVillePartenaire(request.villePartenaireId()));
    }

    private String resolveCode(String requestedCode, String currentCode, boolean create) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            return requestedCode;
        }
        if (create || currentCode == null || currentCode.isBlank()) {
            return codeGeneratorService.generate(CodePrefix.PROFILE);
        }
        return currentCode;
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId est obligatoire");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User introuvable: " + userId));
    }

    private VillePartenaire resolveVillePartenaire(Long villePartenaireId) {
        if (villePartenaireId == null) {
            return null;
        }
        return villePartenaireRepository.findById(villePartenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "VillePartenaire introuvable: " + villePartenaireId));
    }

    private ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getCode(),
                profile.getFirstname(),
                profile.getLastname(),
                profile.getPhone(),
                profile.getRole(),
                profile.isActive(),
                profile.isExpiration(),
                profile.getVersion(),
                profile.getCreation(),
                profile.getLastUpdate(),
                profile.getUser() == null ? null : profile.getUser().getId(),
                profile.getVillePartenaire() == null ? null : profile.getVillePartenaire().getId(),
                profile.getBio(),
                profile.getFacebookUrl(),
                profile.getTwitterUrl(),
                profile.getLinkedinUrl(),
                profile.getInstagramUrl(),
                profile.getCountry(),
                profile.getCity(),
                profile.getState(),
                profile.getPostalCode(),
                profile.getTaxId(),
                profile.isTwoFactorEnabled(),
                profile.getEmail(),
                profile.getSector(),
                profile.getAvatarUrl(),
                profile.getLangueCode()
        );
    }
}
