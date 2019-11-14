package lab5.bmstu;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import javafx.util.Pair;
import scala.concurrent.Future;

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

                    if(req.method() == HttpMethods.GET) {
                        if (req.getUri().path().equals("/")) {
                            String url = req.getUri().query().get("testUrl").orElse("");
                            String count = req.getUri().query().get("count").orElse("");
                            if(url.isEmpty()){
                                return HttpResponse.create().withEntity(ByteString.fromString("TEST URL IS EMPTY"));
                            }
                            if(url.isEmpty()){
                                return HttpResponse.create().withEntity(ByteString.fromString("COUNT IS EMPTY"));
                            }
                            try{
                                Integer countInteger = Integer.parseInt(count);
                                Pair<String, Integer> data = new Pair<>(url, countInteger);
                                CompletionStage<Long> completionStage = Source
                                        .from(Collections.singletonList(data))
                                        .toMat(testSink, Keep.right())
                                        .run(materializer);


                            } catch(Exception e){
                                return HttpResponse.create().withEntity(ByteString.fromString("Some exception happened"));
                            }

                            return HttpResponse.create().withEntity(ContentTypes.TEXT_HTML_UTF8, ByteString.fromString("<html><body>Hello world!</body></html>"));

                        }
                        if (req.getUri().path().equals("/test")) {
                            return HttpResponse.create().withEntity(ByteString.fromString("TEST WORKS!"));
                        } else {
                            req.discardEntityBytes(materializer);
                            return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("NOPE");
                        }
                    }else{
                            req.discardEntityBytes(materializer);
                            return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("NOPE");
                    }
                }).mapAsync(1, Patterns.ask(controlActor, new myMessage(), 5000));
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
