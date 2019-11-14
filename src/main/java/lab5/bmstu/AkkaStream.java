package lab5.bmstu;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.*;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.impl.Completed;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.japi.Pair;
import akka.util.ByteString;
import org.asynchttpclient.Response;
import scala.concurrent.Await;
import scala.concurrent.Future;
import org.asynchttpclient.*;
import scala.concurrent.duration.Duration;
import scala.util.Try;
import static org.asynchttpclient.Dsl.*;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AkkaStream {
    private static ActorRef controlActor;
    private static final Logger logger = LoggerFactory.getLogger(AkkaStream.class);

    public static void main(String[] args) throws IOException {

        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");

        controlActor = system.actorOf(Props.create(CacheActor.class));
        final Http http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        //<вызов метода которому передаем Http, ActorSystem и ActorMaterializer>;
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = Flow.of(HttpRequest.class).map(
                req -> {

                    if (req.method() == HttpMethods.GET) {
                        if (req.getUri().path().equals("/")) {
                            String url = req.getUri().query().get("testUrl").orElse("");
                            String count = req.getUri().query().get("count").orElse("");
                            if (url.isEmpty()) {
                                return HttpResponse.create().withEntity(ByteString.fromString("TEST URL IS EMPTY"));
                            }
                            if (url.isEmpty()) {
                                return HttpResponse.create().withEntity(ByteString.fromString("COUNT IS EMPTY"));
                            }
                            try {
                                Integer countInteger = Integer.parseInt(count);
                                Pair<String, Integer> data = new Pair<>(url, countInteger);
                                boolean flag = false;
                                Source<Pair<String, Integer>, NotUsed> source = Source.from(Collections.singletonList(data));

                                Flow<Pair<String, Integer>, HttpResponse, NotUsed> testSink = Flow.<Pair<String, Integer>>create()
                                        .map(pair -> new Pair<>(HttpRequest.create().withUri(pair.first()), pair.second()))
                                        .mapAsync(1, pair -> {

                                            Future<Object> potentialResult = Patterns
                                                    .ask(
                                                            controlActor,
                                                            new GetMSG(new javafx.util.Pair<>(data.first(), data.second())),
                                                            5000
                                                    );

                                            int value = (int) Await.result(potentialResult, Duration.create(10, TimeUnit.SECONDS));
                                            if(value != -1){
                                                return CompletableFuture.completedFuture(value);
                                            }

                                            //Flow<Pair<HttpRequest, Long>, Pair<Try<HttpResponse>, Long>, NotUsed> httpClient = http.superPool();
                                            Sink<Long, CompletionStage<Integer>> fold = Sink
                                                    .fold(0, (ac, el) -> {
                                                        int testEl = (int) (0 + el);
                                                        return ac + testEl;
                                                    });
                                            return Source.from(Collections.singletonList(pair))
                                                    .toMat(
                                                            Flow.<Pair<HttpRequest, Integer>>create()
                                                                    .mapConcat(p -> Collections.nCopies(p.second(), p.first()))
                                                                    .mapAsync(1, req2 -> {
                                                                        CompletableFuture<Long> future = CompletableFuture.supplyAsync(() ->
                                                                                System.currentTimeMillis()
                                                                        ).thenCompose(start -> CompletableFuture.supplyAsync(() -> {
                                                                            ListenableFuture<Response> whenResponse = asyncHttpClient().prepareGet(req2.getUri().toString()).execute();
                                                                            try {
                                                                                Response response = whenResponse.get();
                                                                            } catch (InterruptedException | ExecutionException e) {
                                                                                System.out.println("KEK");
                                                                            }
                                                                            return System.currentTimeMillis() - start;
                                                                        }));
                                                                        return future;
                                                                    })
                                                                    .toMat(fold, Keep.right()), Keep.right()).run(materializer);
                                        }).map(
                                                sum -> {
                                                    Double middleValue = (double) sum / (double) countInteger;
                                                    return HttpResponse.create().withEntity(ByteString.fromString("response is " + middleValue.toString()));
                                                }
                                        );

                                CompletionStage<HttpResponse> result = source.via(testSink).toMat(Sink.last(), Keep.right()).run(materializer);
                                return result.toCompletableFuture().get();
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                                return HttpResponse.create().withEntity(ByteString.fromString("NUMBER EXCEPTION"));
                            }
                        }else{
                            req.discardEntityBytes(materializer);
                            return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("BAD PATH");
                        }
                    }else{
                        req.discardEntityBytes(materializer);
                        return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("GET ONLY");
                    }
                });
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost("localhost", 8080),
                materializer
        );
        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();
        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }
}
