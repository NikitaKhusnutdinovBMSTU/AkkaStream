package lab5.bmstu;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;

import java.util.HashMap;
import java.util.Map;

public class CacheActor extends AbstractActor {

    private HashMap<String, Map<Integer, Integer>> data = new HashMap<>();

    @Override
    public Receive createReceive() {

        return ReceiveBuilder.create().match(GetMSG.class,
                req -> {

                }).build();
    }
}
