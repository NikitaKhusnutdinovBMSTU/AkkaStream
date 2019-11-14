package lab5.bmstu;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import javafx.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class CacheActor extends AbstractActor {

    private HashMap<String, Map<Integer, Integer>> data = new HashMap<>();

    @Override
    public Receive createReceive() {

        return ReceiveBuilder.create()
                .match(GetMSG.class,
                        msg -> {
                            String url = msg.getURL();
                            int count = msg.getCount();
                            if (data.containsKey(url) && data.get(url).containsKey(count)) {
                                getSender().tell(data.get(url).get(count), ActorRef.noSender());
                            } else {
                                getSender().tell(-1, ActorRef.noSender());
                            }
                        })
                .match(
                        PutMSG.class,
                        msg -> data.put(
                                msg.getURL(),
                                new HashMap<>(msg.getCount(), msg.getTime())
                        )
                )
                .build();
    }
}
