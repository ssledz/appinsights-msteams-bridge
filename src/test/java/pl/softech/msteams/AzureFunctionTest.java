package pl.softech.msteams;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class AzureFunctionTest {

    @Test
    public void testBridgeIfNoHttpBody() throws Exception {
        final HttpResponseMessage resp = testBridge(Optional.empty());

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatus());
        assertEquals("There is no request body", resp.getBody());
    }

    @Test
    public void testBridgeIfPayloadIllegal() throws Exception {
        final var body = Optional.of("{  }");
        final HttpResponseMessage resp = testBridge(body);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatus());
        assertEquals("Illegal payload", resp.getBody());
    }

    @Test
    public void testBridge() throws Exception {
        final var in = AzureFunctionTest.class.getClassLoader().getResource("app-insights-alert-example.json");
        var body = Files.readString(Paths.get(in.toURI()));
        final HttpResponseMessage resp = testBridge(Optional.ofNullable(body));

        var mapper =
                new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .registerModule(new Jdk8Module());
        var decodedResponse = mapper.readValue(resp.getBody().toString(), AzureFunctionResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatus());
        assertEquals("OK", decodedResponse.response());
        assertEquals("http://schema.org/extensions", decodedResponse.cardRequest().context());
        assertEquals("WCUS-R2-Gen2", decodedResponse.cardRequest().summary());
        assertEquals("0076D7", decodedResponse.cardRequest().themeColor());
        assertEquals("MessageCard", decodedResponse.cardRequest().type());
        assertEquals(1, decodedResponse.cardRequest().sections().length);
        assertEquals("WCUS-R2-Gen2", decodedResponse.cardRequest().sections()[0].activityTitle());
        assertEquals(7, decodedResponse.cardRequest().sections()[0].facts().length);
    }

    private HttpResponseMessage testBridge(Optional<String> body) throws Exception {
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);
        doReturn(body).when(req).getBody();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        final HttpClient client = mock(HttpClient.class);
        doAnswer(new Answer<HttpResponse<String>>() {
            @Override
            public HttpResponse<String> answer(InvocationOnMock invocation) throws Throwable {
                var response = mock(HttpResponse.class);
                when(response.body()).thenReturn("OK");
                return response;
            }
        }).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Invoke
        return new AzureFunction(client, "https://null").run(req, context);
    }
}
