package multiroom.radio.rest.controller;

import multiroom.radio.rest.RadioCatalogueService;
import multiroom.radio.rest.api.RadioStationsApi;
import multiroom.radio.rest.dto.RadioStation;
import multiroom.radio.rest.dto.RadioStationPage;
import multiroom.radio.rest.query.RequestParameters;
import multiroom.radio.rest.query.StationQueryParser;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** Thin: checks the names sent, validates the values, hands the query to the service. */
@RestController
public class RadioStationsController implements RadioStationsApi {

    private static final Set<String> SEARCH_PARAMETERS = Set.of("name", "tag", "countryCode", "language", "codec",
            "bitrateMin", "order", "direction", "limit", "offset");

    private final RadioCatalogueService service;

    /** Creates the controller. */
    public RadioStationsController(RadioCatalogueService service) {
        this.service = service;
    }

    @Override
    public RadioStationPage searchRadioStations(String name, String tag, String countryCode, String language,
                                                String codec, Integer bitrateMin, String order, String direction,
                                                Integer limit, Integer offset,
                                                MultiValueMap<String, String> allParameters) {
        RequestParameters.check(allParameters, SEARCH_PARAMETERS);
        return service.search(StationQueryParser.parse(name, tag, language, countryCode, codec, bitrateMin, order,
                direction, limit, offset));
    }

    @Override
    public RadioStation getRadioStation(String uuid, MultiValueMap<String, String> allParameters) {
        RequestParameters.check(allParameters, Set.of());
        return service.station(uuid);
    }
}
