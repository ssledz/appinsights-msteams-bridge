package pl.softech.msteams;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * https://learn.microsoft.com/en-us/microsoftteams/platform/webhooks-and-connectors/how-to/connectors-using?tabs=cURL#example-of-connector-message
 */
public record MSTeamsMessageCard(
        @JsonProperty("@type") String type,
        @JsonProperty("@context") String context,
        String themeColor,
        String summary,
        MessageSection[] sections
) {
    public static MSTeamsMessageCard of(String summary,
                                        MessageSection[] sections) {
        return new MSTeamsMessageCard(
                "MessageCard",
                "http://schema.org/extensions",
                "0076D7",
                summary,
                sections);
    }

    public record MessageSection(
            String activityTitle,
            String activitySubtitle,
            String activityText,
            String activityImage,
            boolean markdown,
            MessageFact[] facts
    ) {
    }

    public record MessageFact(String name, String value) {
    }

}


