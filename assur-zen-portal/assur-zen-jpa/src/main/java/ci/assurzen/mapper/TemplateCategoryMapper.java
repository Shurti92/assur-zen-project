package ci.assurzen.mapper;

import ci.assurzen.dto.TemplateCategoryResponse;
import ci.assurzen.entities.TemplateCategory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TemplateCategoryMapper {

    TemplateCategoryResponse toResponse(TemplateCategory category);
}
