package com.alumni.searcher.phantombuster;

import java.util.Map;


// One LinkedIn profile as returned by the PhantomBuster search export.
// Different phantom versions use slightly different field names,
// so each value is picked from the first name that is present.
public record ScrapedProfile(
        String fullName,
        String headline,
        String jobTitle,
        String location,
        String school,
        String profileUrl
) {

    public static ScrapedProfile fromRawFields(Map<String, Object> fields) {
        String fullName = firstPresent(fields, "fullName", "name");
        if (fullName == null) {
            fullName = joinName(firstPresent(fields, "firstName"), firstPresent(fields, "lastName"));
        }

        return new ScrapedProfile(
                fullName,
                firstPresent(fields, "headline", "job", "description"),
                firstPresent(fields, "title", "jobTitle", "currentJob"),
                firstPresent(fields, "location"),
                firstPresent(fields, "school", "schoolName"),
                firstPresent(fields, "profileUrl", "linkedinProfileUrl", "linkedInProfileUrl", "url")
        );
    }

    private static String firstPresent(Map<String, Object> fields, String... keys) {
        for (String key : keys) {
            Object value = fields.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private static String joinName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null || lastName == null) {
            return firstName != null ? firstName : lastName;
        }
        return firstName + " " + lastName;
    }
}
