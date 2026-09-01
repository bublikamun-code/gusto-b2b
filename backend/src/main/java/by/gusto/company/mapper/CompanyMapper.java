package by.gusto.company.mapper;

import by.gusto.company.dto.CompanyResponse;
import by.gusto.company.entity.Company;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    CompanyResponse toResponse(Company company);

    List<CompanyResponse> toResponseList(List<Company> companies);
}
