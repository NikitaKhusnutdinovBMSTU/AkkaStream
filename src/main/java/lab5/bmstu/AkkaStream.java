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
import scala.util.Try;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

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

                                Flow<Pair<String, Integer>, HttpResponse, NotUsed> testSink = Flow
                                        .<Pair<String, Integer>>create()
                                        .map(pair -> new Pair<>(HttpRequest.create().withUri((pair.first())), pair.second()))
                                        .mapAsync(1, pair -> {
                                            Flow<Pair<HttpRequest, Long>, Pair<Try<HttpResponse>, Long>, NotUsed> httpClient = http.superPool(materializer);
                                            Sink<Pair<Try<HttpResponse>, Long>, CompletionStage<Integer>> fold = Sink
                                                    .fold(0, (ac, el) -> {
                                                        int responseTime = (int) (System.currentTimeMillis() - el.second());
                                                        return ac + responseTime;
                                                    });
                                            return Source.from(Collections.singletonList(pair))
                                                    .toMat(
                                                            Flow.<Pair<HttpRequest, Integer>>create()
                                                                    .mapConcat(p -> Collections.nCopies(p.second(), p.first()))
                                                                    .map(req2 -> new Pair<>(req2, System.currentTimeMillis())).via(httpClient).toMat(fold, Keep.right()), Keep.right()).run(materializer);
                                        }).map(
                                                sum -> {
                                                    Double middleValue = (double) sum / (double) countInteger;
                                                    return HttpResponse.create().withEntity(ByteString.fromString("response is " + middleValue.toString()));
                                                }
                                        );

                                CompletionStage<HttpResponse> result = source.via(testSink).toMat(Sink.last(), Keep.right()).run(materializer);
                                return result.toCompletableFuture().get();
                            } catch (Exception e) {
                                return HttpResponse.create().withEntity(ByteString.fromString("Some exception happened"));
                            }

                        }
                    }

                    req.discardEntityBytes(materializer);
                    return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("NOPE");

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
