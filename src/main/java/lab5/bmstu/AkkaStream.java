package lab5.bmstu;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import akka.japi.Pair;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import scala.util.Try;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class AkkaStream {
    private static ActorRef controlActor;

    public static void main(String[] args) throws IOException {

        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");
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
                                Source<Pair<String, Integer>, NotUsed> source = Source.from(Collections.singletonList(data));

                                Flow<Pair<String, Integer>, HttpResponse, NotUsed> testSink = Flow.<Pair<String, Integer>>create()
                                        .map(pair -> new Pair<>(HttpRequest.create().withUri(pair.first()), pair.second()))
                                        .mapAsync(1, pair -> {
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
                                return HttpResponse.create().withEntity(ByteString.fromString("exception is"));
                            }
                        }else{
                            req.discardEntityBytes(materializer);
                            return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("NOPE");
                        }
                    }else{
                        req.discardEntityBytes(materializer);
                        return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("NOPE");
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
