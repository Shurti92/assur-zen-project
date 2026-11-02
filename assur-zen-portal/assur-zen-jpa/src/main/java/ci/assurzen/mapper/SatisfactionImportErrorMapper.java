package ci.assurzen.mapper;

import ci.assurzen.dto.SatisfactionImportErrorResponse;
import ci.assurzen.entities.SatisfactionImportError;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SatisfactionImportErrorMapper {

    @Mapping(target = "importId", source = "satisfactionImport.id")
    SatisfactionImportErrorResponse toResponse(SatisfactionImportError error);
}
