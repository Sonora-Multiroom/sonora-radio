package multiroom.radio.rest.controller;

import multiroom.radio.rest.RadioCatalogueService;
import multiroom.radio.rest.api.RadioBrowseApi;
import multiroom.radio.rest.dto.RadioCountry;
import multiroom.radio.rest.dto.RadioLanguage;
import multiroom.radio.rest.dto.RadioTagPage;
import multiroom.radio.rest.query.RequestParameters;
import multiroom.radio.rest.query.TagQueryParser;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Thin, like {@link RadioStationsController}: checks the names sent, validates, calls the service. */
@RestController
public class RadioBrowseController implements RadioBrowseApi {

    private static final Set<String> TAG_PARAMETERS = Set.of("name", "limit", "offset");

    private final RadioCatalogueService service;

    /** Creates the controller. */
    public RadioBrowseController(RadioCatalogueService service) {
        this.service = service;
    }

    @Override
    public List<RadioCountry> listRadioCountries(MultiValueMap<String, String> allParameters) {
        RequestParameters.check(allParameters, Set.of());
        return service.countries();
    }

    @Override
    public List<RadioLanguage> listRadioLanguages(MultiValueMap<String, String> allParameters) {
        RequestParameters.check(allParameters, Set.of());
        return service.languages();
    }

    @Override
    public RadioTagPage listRadioTags(String name, Integer limit, Integer offset,
                                      MultiValueMap<String, String> allParameters) {
        RequestParameters.check(allParameters, TAG_PARAMETERS);
        return service.tags(TagQueryParser.parse(name, limit, offset));
    }
}
