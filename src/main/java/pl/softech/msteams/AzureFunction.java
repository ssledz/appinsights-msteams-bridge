package pl.softech.msteams;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import io.vavr.control.Either;
import io.vavr.control.Try;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;

public class AzureFunction {

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .registerModule(new Jdk8Module());

    private final HttpClient httpClient;
    private final String teamsWebHookURL;

    public AzureFunction(HttpClient httpClient, String teamsWebHookURL) {
        this.httpClient = httpClient;
        this.teamsWebHookURL = teamsWebHookURL;
    }

    public AzureFunction() {
        this(HttpClient.newHttpClient(), System.getenv("TEAMS_WEBHOOK_URL"));
    }

    /**
     * curl -d "HTTP Body" {your host}/api/bridge?code=XXX"
     */
    @FunctionName("bridge")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().log(Level.FINEST, "Request:\n" + request.getBody().orElseGet(() -> "{}"));
        return request
                .getBody()
                .map(body -> Try.of(() -> mapper.readValue(body, AppInsightsAlertMessage.class)).toEither())
                .orElseGet(() -> Either.left(new IllegalArgumentException("There is no request body")))
                .flatMap(m -> Try.of(() -> AzureFunction.toMessageCard(m)).toEither())
                .map(m -> handleMessage(m, request, context))
                .getOrElseGet(error -> handleError(error, request, context));
    }

    private HttpResponseMessage handleError(Throwable error, HttpRequestMessage<Optional<String>> request, final ExecutionContext context) {
        if (error instanceof NullPointerException) {
            context.getLogger().log(Level.FINEST, "Error during serving request", error);
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Illegal payload").build();
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(error.getMessage()).build();
    }

    private HttpResponseMessage handleMessage(MSTeamsMessageCard message, HttpRequestMessage<Optional<String>> request,
                                              final ExecutionContext context) {
        try {
            var clientReq = HttpRequest.newBuilder()
                    .uri(new URI(teamsWebHookURL))
                    .headers("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(message)))
                    .build();
            var clResponse = httpClient.send(clientReq, HttpResponse.BodyHandlers.ofString());
            var response = new AzureFunctionResponse(message, clResponse.body());
            context.getLogger().log(Level.FINEST, "Teams Response:\n" + response.response());
            return request.createResponseBuilder(HttpStatus.OK).body(mapper.writeValueAsString(response)).build();
        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Error during serving request", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()).build();
        }
    }

    private static MSTeamsMessageCard toMessageCard(AppInsightsAlertMessage message) {
        var facts = Stream.of(
                        makeFact(message.data().alertContext().LinkToFilteredSearchResultsUI(), "Link To Search Results", v -> String.format("[Link](%s)", v)),
                        makeFact(message.data().alertContext().SearchIntervalStartTimeUtc(), "Search Start Time - UTC", Object::toString),
                        makeFact(message.data().alertContext().SearchIntervalEndtimeUtc(), "Search End Time - UTC", Object::toString),
                        makeFact(message.data().alertContext().SearchQuery(), "Query Executed", Object::toString),
                        makeFact(message.data().alertContext().WorkspaceId(), "Log Analytics Workspace ID", Object::toString),
                        makeFact(message.data().alertContext().ResultCount(), "Query Result Count", Object::toString),
                        makeFact(message.data().alertContext().Threshold(), "Threshold", Object::toString)
                )
                .reduce(Stream::concat)
                .orElseGet(() -> Stream.empty())
                .toArray(MSTeamsMessageCard.MessageFact[]::new);

        return MSTeamsMessageCard.of(
                message.data().essentials().alertRule(),
                new MSTeamsMessageCard.MessageSection[]{
                        new MSTeamsMessageCard.MessageSection(
                                message.data().essentials().alertRule(),
                                message.data().essentials().description(),
                                String.format(
                                        "Alert <em>%s</em> has been %s",
                                        message.data().essentials().alertRule(),
                                        strong(
                                                message.data().essentials().monitorCondition(),
                                                getColour(message.data().essentials().monitorCondition())
                                        )
                                ),
                                getCat(message.data().essentials().monitorCondition()),
                                true,
                                facts
                        )});
    }

    private static String selectFirstOption(String defaultValue, Supplier<Optional<String>>... opts) {
        return Arrays.stream(opts)
                .flatMap(m -> m.get().stream())
                .findFirst()
                .orElseGet(() -> defaultValue);
    }

    private static String getCat(String cond) {
        return selectFirstOption("https://adaptivecards.io/content/cats/3.png",
                () -> Objects.equals(cond, "Fired") ? Optional.of("https://adaptivecards.io/content/cats/1.png") : Optional.empty()
        );
    }

    private static String getColour(String cond) {
        return selectFirstOption("green",
                () -> Objects.equals(cond, "Fired") ? Optional.of("red") : Optional.empty()
        );
    }

    private static String strong(String txt, String color) {
        return String.format("<strong style=\"color: %s\">%s</strong>", color, txt);
    }

    private static <T> Stream<MSTeamsMessageCard.MessageFact> makeFact(Optional<T> value, String label, Function<T, String> formatter) {
        return value
                .map(l -> new MSTeamsMessageCard.MessageFact(label, formatter.apply(l)))
                .stream();
    }
}
