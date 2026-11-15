package multiroom.radio.rest;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import multiroom.radio.rest.api.RadioBrowseApi;
import multiroom.radio.rest.api.RadioStationsApi;
import multiroom.radio.rest.controller.RadioFallbackController;
import multiroom.radio.rest.dto.RadioCountry;
import multiroom.radio.rest.dto.RadioLanguage;
import multiroom.radio.rest.dto.RadioProblem;
import multiroom.radio.rest.dto.RadioStation;
import multiroom.radio.rest.dto.RadioStationPage;
import multiroom.radio.rest.dto.RadioTag;
import multiroom.radio.rest.dto.RadioTagPage;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Springdoc is not a dependency of this module (the hub's rest module supplies it at run time), so the
 * description is asserted by reflection on the annotations the generator reads. The generated
 * {@code /api-docs} itself is compared with {@code contracts/radio-rest-api.yaml} in the live check.
 */
class RadioApiDescriptionTest {

    /** {@code @Schema} cannot sit on a record component itself; it is carried to the accessor. */
    private static Schema schema(RecordComponent component) {
        return component.getAccessor().getAnnotation(Schema.class);
    }

    private static Method method(Class<?> api, String name) {
        return Arrays.stream(api.getMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    private static Parameter parameter(Method method, String name) {
        return Arrays.stream(method.getParameters())
                .filter(p -> p.isAnnotationPresent(RequestParam.class) && name.equals(p.getAnnotation(RequestParam.class).name()))
                .map(p -> p.getAnnotation(Parameter.class))
                .findFirst().orElseThrow(() -> new AssertionError("no @Parameter for " + name));
    }

    private static Set<String> statuses(Method method) {
        return Arrays.stream(method.getAnnotation(Operation.class).responses()).map(ApiResponse::responseCode)
                .collect(Collectors.toSet());
    }

    @Test
    void bothApisCarryTheRadioTag() {
        assertThat(RadioStationsApi.class.getAnnotation(Tag.class).name()).isEqualTo("Radio");
        assertThat(RadioBrowseApi.class.getAnnotation(Tag.class).name()).isEqualTo("Radio");
    }

    @Test
    void theFiveOperationIdsAreStable() {
        assertThat(method(RadioStationsApi.class, "searchRadioStations").getAnnotation(Operation.class).operationId())
                .isEqualTo("searchRadioStations");
        assertThat(method(RadioStationsApi.class, "getRadioStation").getAnnotation(Operation.class).operationId())
                .isEqualTo("getRadioStation");
        assertThat(method(RadioBrowseApi.class, "listRadioCountries").getAnnotation(Operation.class).operationId())
                .isEqualTo("listRadioCountries");
        assertThat(method(RadioBrowseApi.class, "listRadioLanguages").getAnnotation(Operation.class).operationId())
                .isEqualTo("listRadioLanguages");
        assertThat(method(RadioBrowseApi.class, "listRadioTags").getAnnotation(Operation.class).operationId())
                .isEqualTo("listRadioTags");
    }

    @Test
    void searchParametersCarryTheBoundsEnumsAndDefaultsOfTheContract() {
        Method search = method(RadioStationsApi.class, "searchRadioStations");
        Schema limit = parameter(search, "limit").schema();
        assertThat(limit.minimum()).isEqualTo("1");
        assertThat(limit.maximum()).isEqualTo("100");
        assertThat(limit.defaultValue()).isEqualTo("20");
        Schema offset = parameter(search, "offset").schema();
        assertThat(offset.minimum()).isEqualTo("0");
        assertThat(offset.maximum()).isEqualTo("10000");
        assertThat(offset.defaultValue()).isEqualTo("0");
        Schema order = parameter(search, "order").schema();
        assertThat(order.allowableValues()).containsExactly("name", "votes", "clickCount", "bitrate");
        assertThat(order.defaultValue()).isEqualTo("votes");
        assertThat(parameter(search, "direction").schema().allowableValues()).containsExactly("asc", "desc");
        Schema bitrate = parameter(search, "bitrateMin").schema();
        assertThat(bitrate.minimum()).isEqualTo("0");
        assertThat(bitrate.maximum()).isEqualTo("1000000");
        assertThat(parameter(search, "name").schema().maxLength()).isEqualTo(100);
        assertThat(parameter(search, "codec").schema().maxLength()).isEqualTo(32);
        assertThat(parameter(search, "countryCode").schema().pattern()).isEqualTo("^[A-Za-z]{2}$");
    }

    @Test
    void tagParametersCarryTheSameBounds() {
        Method tags = method(RadioBrowseApi.class, "listRadioTags");
        assertThat(parameter(tags, "limit").schema().maximum()).isEqualTo("100");
        assertThat(parameter(tags, "offset").schema().maximum()).isEqualTo("10000");
        assertThat(parameter(tags, "name").schema().maxLength()).isEqualTo(100);
    }

    @Test
    void theCatchAllParameterMapIsHiddenFromTheDescription() {
        for (Method m : List.of(method(RadioStationsApi.class, "searchRadioStations"),
                method(RadioStationsApi.class, "getRadioStation"), method(RadioBrowseApi.class, "listRadioCountries"),
                method(RadioBrowseApi.class, "listRadioLanguages"), method(RadioBrowseApi.class, "listRadioTags"))) {
            java.lang.reflect.Parameter map = m.getParameters()[m.getParameterCount() - 1];
            assertThat(map.isAnnotationPresent(Parameter.class)).as(m.getName()).isTrue();
            assertThat(map.getAnnotation(Parameter.class).hidden()).as(m.getName()).isTrue();
        }
    }

    @Test
    void everyDocumentedErrorStatusIsDeclared() {
        assertThat(statuses(method(RadioStationsApi.class, "searchRadioStations")))
                .contains("200", "400", "502", "503", "default");
        assertThat(statuses(method(RadioStationsApi.class, "getRadioStation")))
                .contains("200", "400", "404", "502", "503", "default");
        for (String name : List.of("listRadioCountries", "listRadioLanguages", "listRadioTags")) {
            assertThat(statuses(method(RadioBrowseApi.class, name))).contains("200", "400", "502", "503", "default");
        }
    }

    @Test
    void theSchemaNamesAreTheRadioShapes() {
        Map<Class<?>, String> expected = Map.of(RadioStation.class, "RadioStation",
                RadioStationPage.class, "RadioStationPage", RadioCountry.class, "RadioCountry",
                RadioLanguage.class, "RadioLanguage", RadioTag.class, "RadioTag", RadioTagPage.class, "RadioTagPage",
                RadioProblem.class, "RadioProblem");
        expected.forEach((type, name) -> assertThat(type.getAnnotation(Schema.class).name()).as(type.getSimpleName())
                .isEqualTo(name));
    }

    @Test
    void nullableFieldsAreMarkedAndEveryComponentIsDescribed() {
        Set<String> nullable = Arrays.stream(RadioStation.class.getRecordComponents())
                .filter(c -> schema(c) != null && schema(c).nullable())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(nullable).containsExactlyInAnyOrder("name", "favicon", "homepage", "countryCode", "codec", "bitrate");
        assertThat(schema(RadioStationPage.class.getRecordComponents()[1]).nullable()).isTrue();
        assertThat(schema(RadioTagPage.class.getRecordComponents()[1]).nullable()).isTrue();
        assertThat(schema(RadioLanguage.class.getRecordComponents()[1]).nullable()).isTrue();
        for (Class<?> type : List.of(RadioStation.class, RadioStationPage.class, RadioCountry.class, RadioLanguage.class,
                RadioTag.class, RadioTagPage.class, RadioProblem.class)) {
            for (RecordComponent c : type.getRecordComponents()) {
                assertThat(schema(c)).as(type.getSimpleName() + "." + c.getName()).isNotNull();
                assertThat(schema(c).description()).as(type.getSimpleName() + "." + c.getName()).isNotBlank();
            }
        }
    }

    @Test
    void theFallbackControllerIsHidden() {
        assertThat(RadioFallbackController.class.isAnnotationPresent(Hidden.class)).isTrue();
    }
}
