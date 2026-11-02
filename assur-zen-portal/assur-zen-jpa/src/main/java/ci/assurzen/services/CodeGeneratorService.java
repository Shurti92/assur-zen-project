package ci.assurzen.services;

import ci.assurzen.entities.SequenceEntity;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.SequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Year;


import java.time.Year;

@Service
@RequiredArgsConstructor
public class CodeGeneratorService {

    private final SequenceRepository repository;

    /**
     * Exemple :
     * PRF2026000001
     * USR2026000002
     * PAR2026000003
     */
    public String generate(CodePrefix prefix) {

        SequenceEntity sequence = repository.save(new SequenceEntity());

        return prefix.getValue()
                + Year.now().getValue()
                + String.format("%06d", sequence.getId());

    }

}
