package lab5.bmstu;

import javafx.util.Pair;

public class PutMSG {
    private Pair<String, Pair<Integer, Integer>> msg;

    public PutMSG(akka.japi.Pair<String, akka.japi.Pair<Integer, Integer>> msg){
        this.msg = msg;
    }

    public String getURL(){
        return msg.getKey();
    }

    public int getCount(){
        return msg.getValue().getKey();
    }

    public long getTime(){
        return msg.getValue().getValue();
    }
}
