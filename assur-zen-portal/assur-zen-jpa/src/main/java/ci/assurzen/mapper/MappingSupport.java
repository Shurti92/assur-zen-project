package ci.assurzen.mapper;

import ci.assurzen.entities.Insured;
import ci.assurzen.entities.TemplateVariable;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MappingSupport {

    private final ProfileRepository profileRepository;

    @Named("userDisplayName")
    public String userDisplayName(User user) {
        if (user == null) {
            return null;
        }
        return profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFirstname() + " " + profile.getLastname())
                .orElse(user.getUsername());
    }

    @Named("insuredDisplayName")
    public String insuredDisplayName(Insured insured) {
        if (insured == null) {
            return null;
        }
        return insured.getPrenoms() + " " + insured.getNom();
    }

    @Named("categorieNps")
    public String categorieNps(Integer scoreNps) {
        if (scoreNps == null) {
            return null;
        }
        if (scoreNps >= 9) {
            return "Promoteur";
        }
        if (scoreNps >= 7) {
            return "Passif";
        }
        return "Detracteur";
    }

    @Named("variableCodes")
    public List<String> variableCodes(Set<TemplateVariable> variables) {
        if (variables == null) {
            return List.of();
        }
        return variables.stream().map(TemplateVariable::getCode).sorted().toList();
    }
}
