package ci.assurzen.mapper;

import ci.assurzen.dto.TemplateVariableResponse;
import ci.assurzen.entities.TemplateVariable;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TemplateVariableMapper {

    TemplateVariableResponse toResponse(TemplateVariable variable);
}
