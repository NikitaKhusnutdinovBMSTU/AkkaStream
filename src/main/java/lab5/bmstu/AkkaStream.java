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
        final ActorMaterializer materializer =
                ActorMaterializer.create(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = Flow.of(HttpRequest.class).map(
                request -> {
                    if(request.method() == HttpMethods.GET) {
                        if (request.getUri().path().equals("/")) {
                            String url =  request.getUri().query().get("testUrl").get();
                            int count =  Integer.parseInt(request.getUri().query().get("count").get());
                            Pair<String, Integer> data = new Pair<>(url,count);
                            try {
                                Source<Pair<String, Integer>, NotUsed> source = Source.from(Collections.singleton(data));
                                Flow<Pair<String, Integer>, HttpResponse, NotUsed> flow = Flow.<Pair<String, Integer>>create()
                                        .map(pair -> new Pair<>(HttpRequest.create().withUri(pair.first()), pair.second())).
                                                mapAsync(1, pair -> {
                                                    Sink<Long, CompletionStage<Integer>> fold = Sink.fold(0,
                                                            (accumulator, element) -> {
                                                                int responseTime = (int) (0 + element);
                                                                return accumulator + responseTime;
                                                            });
                                                    return Source.from(Collections.singleton(pair)).toMat(Flow.<Pair<HttpRequest, Integer>>create().
                                                            mapConcat(p -> Collections.nCopies(p.second(), p.first())).
                                                            mapAsync(1, request2 ->{
                                                                CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> System.currentTimeMillis())
                                                                        .thenCompose(start -> CompletableFuture.supplyAsync(() -> {
                                                                            ListenableFuture<Response> whenResponse = asyncHttpClient().prepareGet(request2.getUri().toString()).execute();
                                                                            try {
                                                                                Response response = whenResponse.get();
                                                                            } catch (InterruptedException | ExecutionException e) {
                                                                                System.out.println(e);
                                                                            }
                                                                            return System.currentTimeMillis() - start;
                                                                        }));
                                                                return future;
                                                            })
                                                            .toMat(fold, Keep.right()), Keep.right()).run(materializer);
                                                }).map(sum -> {
                                            Double middleValue = (double)sum/(double)count;
                                            return HttpResponse.create().withEntity(ByteString.fromString(middleValue.toString()));
                                        });
                                CompletionStage<HttpResponse> result = source.via(flow).toMat(Sink.last(), Keep.right()).run(materializer);
                                return result.toCompletableFuture().get();
                            } catch (Exception e) {
                                e.printStackTrace();
                                return HttpResponse.create().withEntity(ByteString.fromString(e.toString()));
                            }
                        } else {
                            request.discardEntityBytes(materializer);
                            return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("NO");
                        }
                    } else {
                        request.discardEntityBytes(materializer);
                        return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("NO");

                    }
                }
        );
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost("localhost", 8080),
                materializer
        );
        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();
        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate());
    }
}
