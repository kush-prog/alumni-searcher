package com.alumni.searcher.config;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    public static final String ALUMNI_TAG = "Alumni";

    private static final String ERROR_SCHEMA = "ErrorResponse";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/" + ERROR_SCHEMA;

    /** Errors any endpoint can return. */
    private static final Map<String, String> COMMON_ERRORS = orderedMap(
            "500", "Unexpected server error. Details are logged, not returned.",
            "503", "The database cannot be reached.");

    /** Extra errors for the search endpoint, which validates input and calls PhantomBuster. */
    private static final Map<String, String> SEARCH_ERRORS = orderedMap(
            "400", "Missing or invalid fields, broken JSON, or a pass-out year that is not a number.",
            "409", "Another search saved the same profiles at the same moment. Retrying is safe.",
            "415", "The request body was not sent as JSON.",
            "502", "PhantomBuster is not configured, rejected the API key, is unreachable, or the job failed.",
            "504", "The PhantomBuster search did not finish in the allowed time.");

    @Bean
    public OpenAPI alumniSearcherOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Alumni LinkedIn Profile Searcher API")
                        .version("1.0.0")
                        .description("""
                                Searches LinkedIn for alumni of a university who hold a given role, \
                                using the PhantomBuster API, and stores the profiles in PostgreSQL.

                                Every reply uses the same envelope: `status` is `success` or `error`; \
                                successful replies carry `data`, failed ones carry `message` \
                                (and `errors` for invalid fields).""")
                        .contact(new Contact().name("Alumni Searcher")))
                .addTagsItem(new Tag().name(ALUMNI_TAG).description("Search for alumni and list saved profiles"));
    }

    // Runs after springdoc has generated the document, so the error schema registered here
    // is not replaced by the generated components.
    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(ERROR_SCHEMA, errorSchema());

            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((method, operation) -> {
                        if (method == PathItem.HttpMethod.POST && path.endsWith("/search")) {
                            addErrors(operation, SEARCH_ERRORS);
                        }
                        addErrors(operation, COMMON_ERRORS);
                    }));
        };
    }

    private static void addErrors(Operation operation, Map<String, String> errors) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        errors.forEach((code, description) -> operation.getResponses().putIfAbsent(code, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF))))));
    }

    private static Schema<?> errorSchema() {
        return new ObjectSchema()
                .description("Reply sent for every error")
                .addProperty("status", new StringSchema().example("error"))
                .addProperty("message", new StringSchema().example("Some fields are missing or invalid"))
                .addProperty("errors", new ObjectSchema()
                        .description("Field name → problem. Present only when validation fails.")
                        .additionalProperties(new StringSchema())
                        .example(Json.mapper().createObjectNode().put("university", "University name is required")));
    }

    private static Map<String, String> orderedMap(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
