package pl.softech.msteams;

import java.util.Optional;

/**
 * https://learn.microsoft.com/en-us/azure/azure-monitor/alerts/alerts-payload-samples#sample-alert-payload
 */
public record AppInsightsAlertMessage(AlertData data) {

    public record AlertData(AlertEssentials essentials, AlertContext alertContext) {
    }

    public record AlertEssentials(
            String alertRule,
            String severity,
            String description,
            String monitorCondition
    ) {
    }

    public record AlertContext(
            Optional<String> LinkToFilteredSearchResultsUI,
            Optional<String> SearchIntervalStartTimeUtc,
            Optional<String> SearchIntervalEndtimeUtc,
            Optional<String> SearchQuery,
            Optional<String> WorkspaceId,
            Optional<Integer> ResultCount,
            Optional<Long> Threshold,
            Optional<Boolean> IncludedSearchResults
    ) {
    }


}
